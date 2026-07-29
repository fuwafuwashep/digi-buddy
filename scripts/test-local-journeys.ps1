param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$ZipCode = "32539"
)

$ErrorActionPreference = "Stop"
$checks = [System.Collections.Generic.List[object]]::new()

function Invoke-DigibuddyApi {
    param(
        [string]$Method,
        [string]$Path,
        [string]$AccessToken = "",
        [object]$Body = $null,
        [hashtable]$ExtraHeaders = @{}
    )

    $headers = @{}
    if ($AccessToken) { $headers.Authorization = "Bearer $AccessToken" }
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }

    try {
        $response = Invoke-WebRequest @parameters
        $data = if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $data; Raw = $response.Content }
    } catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
        $stream = $response.GetResponseStream()
        $reader = [System.IO.StreamReader]::new($stream)
        $raw = $reader.ReadToEnd()
        $reader.Dispose()
        $data = if ($raw) { try { $raw | ConvertFrom-Json } catch { $null } } else { $null }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $data; Raw = $raw }
    }
}

function Confirm-Check {
    param([string]$Name, [bool]$Passed, [string]$Detail = "")
    $checks.Add([pscustomobject]@{ Name = $Name; Passed = $Passed; Detail = $Detail })
    if (-not $Passed) { throw "Journey check failed: $Name. $Detail" }
}

function Invoke-DigibuddyUpload {
    param([string]$Path, [byte[]]$Bytes, [string]$ContentType)
    try {
        $response = Invoke-WebRequest -Method PUT -Uri "$BaseUrl$Path" -Body $Bytes -ContentType $ContentType -UseBasicParsing
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $null; Raw = $response.Content }
    } catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $null; Raw = "" }
    }
}

function Confirm-Status {
    param([string]$Name, [object]$Response, [int]$Expected = 200)
    $detail = "Expected HTTP $Expected; received HTTP $($Response.Status). $($Response.Raw)"
    Confirm-Check $Name ($Response.Status -eq $Expected) $detail
}

function New-PhoneSession {
    param([string]$PhoneNumber, [string]$DeviceId, [string]$DeviceName)
    $challenge = Invoke-DigibuddyApi POST "/api/v1/auth/phone/verifications" -Body @{
        phoneNumber = $PhoneNumber
        defaultRegion = "US"
    }
    Confirm-Status "Start phone verification for $DeviceName" $challenge
    Confirm-Check "Development OTP is available for $DeviceName" ($challenge.Data.developmentCode -match '^\d{6}$')
    $verification = Invoke-DigibuddyApi POST "/api/v1/auth/phone/verify" -Body @{
        attemptId = $challenge.Data.attemptId
        code = $challenge.Data.developmentCode
        deviceId = $DeviceId
        deviceName = $DeviceName
    }
    Confirm-Status "Complete phone verification for $DeviceName" $verification
    return $verification.Data
}

function Save-HelperStep {
    param([string]$Token, [string]$Step, [hashtable]$Payload)
    $response = Invoke-DigibuddyApi PUT "/api/v1/helper/application/steps/$Step" $Token $Payload
    Confirm-Status "Save helper step $Step" $response
}

$health = Invoke-DigibuddyApi GET "/health"
Confirm-Status "Backend health" $health
Confirm-Check "Backend reports healthy" ($health.Data.status -eq "ok")

$normalization = Invoke-DigibuddyApi POST "/api/v1/auth/phone/normalize" -Body @{
    phoneNumber = "(850) 555-0182"
    defaultRegion = "US"
}
Confirm-Status "Normalize a Crestview-area phone number" $normalization
Confirm-Check "Phone number is normalized to E.164" ($normalization.Data.e164 -eq "+18505550182")

$customer = New-PhoneSession "850-555-0182" "journey-customer-32539" "Customer journey test"
$helper = New-PhoneSession "850-555-0183" "journey-helper-32539" "Helper journey test"

$unauthorized = Invoke-DigibuddyApi GET "/api/v1/customer/profile"
Confirm-Status "Protected customer profile rejects anonymous access" $unauthorized 401

$onboarding = Invoke-DigibuddyApi POST "/api/v1/customer/onboarding" $customer.accessToken @{
    firstName = "Maria"
    lastName = "Rivera"
    zipCode = $ZipCode
    locationPermission = "NOT_REQUESTED"
    notificationPermission = "NOT_REQUESTED"
    technologyPreferences = @("WI_FI", "COMPUTERS")
}
Confirm-Status "Complete simple customer onboarding" $onboarding
Confirm-Check "Customer profile uses ZIP $ZipCode" ($onboarding.Data.zipCode -eq $ZipCode)
Confirm-Check "Customer Wi-Fi preference is retained" ("WI_FI" -in @($onboarding.Data.technologyPreferences))

$address = Invoke-DigibuddyApi POST "/api/v1/customer/profile/addresses" $customer.accessToken @{
    label = "Home"
    line1 = "123 Test Lane"
    city = "Crestview"
    region = "FL"
    zipCode = $ZipCode
}
Confirm-Status "Save a customer address in ZIP $ZipCode" $address
Confirm-Check "Saved address remains in ZIP $ZipCode" ($address.Data.savedAddresses[0].zipCode -eq $ZipCode)

$accessibility = Invoke-DigibuddyApi PUT "/api/v1/customer/settings/accessibility" $customer.accessToken @{
    followSystemTextSize = $true
    extraLargeText = $true
    highContrast = $true
    reducedMotion = $true
    simplifiedInstructions = $true
}
Confirm-Status "Update accessibility settings" $accessibility
Confirm-Check "Accessibility choices are persisted" (
    $accessibility.Data.settings.extraLargeText -and
    $accessibility.Data.settings.highContrast -and
    $accessibility.Data.settings.reducedMotion -and
    (
        $null -eq $accessibility.Data.settings.simplifiedInstructions -or
        $accessibility.Data.settings.simplifiedInstructions
    )
)

$beforeApproval = Invoke-DigibuddyApi GET "/api/v1/customer/helpers/search?zipCode=$ZipCode&remoteService=true&page=1&pageSize=50" $customer.accessToken
Confirm-Status "Search approved helpers in ZIP $ZipCode before helper approval" $beforeApproval
Confirm-Check "Unapproved helper is absent from customer search" ($null -eq (@($beforeApproval.Data.items) | Where-Object displayName -eq "Charles Han"))
Confirm-Check "Customer search contains no fictional helpers" (@($beforeApproval.Data.items).Count -eq 0)

$emptyRequests = Invoke-DigibuddyApi GET "/api/v1/helper/bookings/requests" $helper.accessToken
Confirm-Status "Unapproved account cannot refresh paid requests" $emptyRequests 403

Save-HelperStep $helper.accessToken "LEGAL_NAME" @{
    values = @{ legalFirstName = "Charles"; legalLastName = "Han" }
}
Save-HelperStep $helper.accessToken "PUBLIC_PROFILE" @{
    values = @{
        displayName = "Charles Han"
        headline = "Patient help with computers and Wi-Fi"
        biography = "I help neighbors solve everyday technology problems using calm, easy-to-follow steps."
    }
}
Save-HelperStep $helper.accessToken "HOME_AND_SERVICE_MODE" @{
    values = @{ homeZip = $ZipCode; serviceMode = "BOTH" }
}
Save-HelperStep $helper.accessToken "SERVICE_AREA" @{
    values = @{ serviceAreaSummary = "Crestview and nearby Okaloosa County communities" }
}
Save-HelperStep $helper.accessToken "SKILLS" @{
    listValues = @{ skillIds = @("windows", "home-networking", "printers") }
}
Save-HelperStep $helper.accessToken "SERVICES" @{
    listValues = @{ serviceCategoryIds = @("computer-help", "wifi-internet", "printer-setup") }
}
Save-HelperStep $helper.accessToken "EXPERIENCE" @{
    values = @{ yearsExperience = "6" }
}
Save-HelperStep $helper.accessToken "LANGUAGES" @{
    listValues = @{ languages = @("en", "es") }
}
Save-HelperStep $helper.accessToken "PRICING" @{
    booleanValues = @{ platformPricingAcknowledged = $true }
}
Save-HelperStep $helper.accessToken "AVAILABILITY" @{
    values = @{ availabilitySummary = "Weekday evenings and Saturday mornings" }
}
Save-HelperStep $helper.accessToken "TERMS_AND_POLICIES" @{
    booleanValues = @{ accepted = $true }
}
Save-HelperStep $helper.accessToken "PAYOUT_ONBOARDING" @{
    booleanValues = @{ placeholderAcknowledged = $true }
}

$submission = Invoke-DigibuddyApi POST "/api/v1/helper/application/submit" $helper.accessToken
Confirm-Status "Submit complete helper application" $submission
Confirm-Check "Submitted helper is under review" ($submission.Data.status -eq "UNDER_REVIEW")

$underReviewSearch = Invoke-DigibuddyApi GET "/api/v1/customer/helpers/search?zipCode=$ZipCode&remoteService=true&page=1&pageSize=50" $customer.accessToken
Confirm-Status "Search while helper is under review" $underReviewSearch
Confirm-Check "Under-review helper remains hidden" ($null -eq (@($underReviewSearch.Data.items) | Where-Object displayName -eq "Charles Han"))

$approval = Invoke-DigibuddyApi POST "/api/v1/helper/application/development/approve" $helper.accessToken
Confirm-Status "Approve helper through the labeled local-development adapter" $approval
Confirm-Check "Local helper approval reaches APPROVED" ($approval.Data.status -eq "APPROVED")

$approvedSearch = Invoke-DigibuddyApi GET "/api/v1/customer/helpers/search?zipCode=$ZipCode&remoteService=true&page=1&pageSize=50" $customer.accessToken
Confirm-Status "Search ZIP $ZipCode after helper approval" $approvedSearch
$helperSummary = @($approvedSearch.Data.items) | Where-Object displayName -eq "Charles Han" | Select-Object -First 1
Confirm-Check "Approved helper appears in customer search" ($null -ne $helperSummary)
Confirm-Check "Public helper ID is separate from private account ID" ($helperSummary.helperId -ne $helper.userId)
Confirm-Check "Platform sets the displayed starting price to `$29" ($helperSummary.startingPriceCents -eq 2900)
Confirm-Check "Catalog response does not expose private phone or home ZIP fields" (
    $approvedSearch.Raw -notmatch '"phone(Number)?"\s*:' -and
    $approvedSearch.Raw -notmatch '"homeZip"\s*:'
)

$emptyRequests = Invoke-DigibuddyApi GET "/api/v1/helper/bookings/requests" $helper.accessToken
Confirm-Status "Approved helper refreshes requests before customers book" $emptyRequests
Confirm-Check "Helper request list contains no fictional requests" (@($emptyRequests.Data.items).Count -eq 0)

$profile = Invoke-DigibuddyApi GET "/api/v1/customer/helpers/$($helperSummary.helperId)/profile" $customer.accessToken
Confirm-Status "Open approved helper profile" $profile
Confirm-Check "Helper profile has platform prices" (
    (@($profile.Data.services) | Measure-Object -Minimum startingPriceCents).Minimum -eq 2900
)

$directConversation = Invoke-DigibuddyApi POST "/api/v1/customer/conversations/helper" $customer.accessToken @{
    helperId = $helperSummary.helperId
}
Confirm-Status "Customer starts a conversation with the created helper account" $directConversation
$customerMessage = Invoke-DigibuddyApi POST "/api/v1/customer/conversations/$($directConversation.Data.conversationId)/messages" $customer.accessToken @{
    clientMessageId = "journey-customer-message-32539"
    body = "Can you help me with my Wi-Fi?"
}
Confirm-Status "Customer sends a message to the helper" $customerMessage
$helperConversations = Invoke-DigibuddyApi GET "/api/v1/helper/conversations" $helper.accessToken
Confirm-Status "Helper refreshes real conversations" $helperConversations
$helperConversation = @($helperConversations.Data.items) | Where-Object conversationId -eq $directConversation.Data.conversationId | Select-Object -First 1
Confirm-Check "Customer message appears in the helper app" ($null -ne $helperConversation)
$helperMessage = Invoke-DigibuddyApi POST "/api/v1/helper/conversations/$($directConversation.Data.conversationId)/messages" $helper.accessToken @{
    clientMessageId = "journey-helper-message-32539"
    body = "Yes. I can help with that."
}
Confirm-Status "Helper replies in the same conversation" $helperMessage
$sharedMessages = Invoke-DigibuddyApi GET "/api/v1/customer/conversations/$($directConversation.Data.conversationId)/messages" $customer.accessToken
Confirm-Status "Customer refreshes the shared conversation" $sharedMessages
Confirm-Check "Both customer and helper messages are visible" (@($sharedMessages.Data.items).Count -eq 2)

$pause = Invoke-DigibuddyApi POST "/api/v1/helper/application/pause" $helper.accessToken
Confirm-Status "Pause helper availability" $pause
$pausedSearch = Invoke-DigibuddyApi GET "/api/v1/customer/helpers/search?zipCode=$ZipCode&remoteService=true&page=1&pageSize=50" $customer.accessToken
Confirm-Status "Search while helper is paused" $pausedSearch
Confirm-Check "Paused helper is removed from customer search" ($null -eq (@($pausedSearch.Data.items) | Where-Object displayName -eq "Charles Han"))

$resume = Invoke-DigibuddyApi POST "/api/v1/helper/application/resume" $helper.accessToken
Confirm-Status "Resume helper availability" $resume
$resumedSearch = Invoke-DigibuddyApi GET "/api/v1/customer/helpers/search?zipCode=$ZipCode&remoteService=true&page=1&pageSize=50" $customer.accessToken
Confirm-Status "Search after helper resumes" $resumedSearch
Confirm-Check "Resumed helper returns to customer search" ($null -ne (@($resumedSearch.Data.items) | Where-Object displayName -eq "Charles Han"))

$service = @($profile.Data.services) | Select-Object -First 1
$remoteBookingBody = @{
    helperId = $helperSummary.helperId
    helperDisplayName = $helperSummary.displayName
    serviceCategory = $service.categorySlug
    serviceName = $service.name
    serviceMode = "REMOTE"
    problemDescription = "My laptop disconnects from Wi-Fi during video calls."
    scheduledStart = "2026-08-01T15:00:00Z"
    scheduledEnd = "2026-08-01T16:00:00Z"
    pricingType = $service.pricingType
    expectedLaborCents = 1
    cancellationTermsAccepted = $true
    paymentMethodPlaceholder = "Development payment method"
}
$remoteBooking = Invoke-DigibuddyApi POST "/api/v1/customer/bookings" $customer.accessToken $remoteBookingBody @{
    "Idempotency-Key" = "journey-remote-32539"
}
Confirm-Status "Request quick remote help" $remoteBooking
Confirm-Check "Backend ignores a tampered customer price and charges `$29" ($remoteBooking.Data.summary.price.totalCents -eq 2900)

$remoteReplay = Invoke-DigibuddyApi POST "/api/v1/customer/bookings" $customer.accessToken $remoteBookingBody @{
    "Idempotency-Key" = "journey-remote-32539"
}
Confirm-Status "Replay remote booking safely" $remoteReplay
Confirm-Check "Booking idempotency prevents a duplicate" (
    $remoteReplay.Data.summary.bookingId -eq $remoteBooking.Data.summary.bookingId
)

$inHomeBooking = Invoke-DigibuddyApi POST "/api/v1/customer/bookings" $customer.accessToken @{
    helperId = $helperSummary.helperId
    helperDisplayName = $helperSummary.displayName
    serviceCategory = $service.categorySlug
    serviceName = $service.name
    serviceMode = "IN_PERSON"
    problemDescription = "My printer is connected but every print job remains stuck in the queue."
    address = @{
        label = "Service address"
        line1 = "123 Test Lane"
        city = "Crestview"
        region = "FL"
        zipCode = $ZipCode
    }
    scheduledStart = "2026-08-02T15:00:00Z"
    scheduledEnd = "2026-08-02T16:00:00Z"
    pricingType = $service.pricingType
    expectedLaborCents = 1
    cancellationTermsAccepted = $true
    paymentMethodPlaceholder = "Development payment method"
} @{
    "Idempotency-Key" = "journey-inhome-32539"
}
Confirm-Status "Request an in-home visit in ZIP $ZipCode" $inHomeBooking
Confirm-Check "Backend ignores a tampered customer price and charges `$79" ($inHomeBooking.Data.summary.price.totalCents -eq 7900)
Confirm-Check "In-home booking retains Crestview address" (
    $inHomeBooking.Data.address.city -eq "Crestview" -and $inHomeBooking.Data.address.zipCode -eq $ZipCode
)

$helperRequests = Invoke-DigibuddyApi GET "/api/v1/helper/bookings/requests" $helper.accessToken
Confirm-Status "Helper refreshes customer requests" $helperRequests
Confirm-Check "Both real customer requests appear in Helpers" (@($helperRequests.Data.items).Count -eq 2)
$remoteHelperRequest = @($helperRequests.Data.items) | Where-Object bookingId -eq $remoteBooking.Data.summary.bookingId | Select-Object -First 1
Confirm-Check "Request shows the real customer's public name" ($remoteHelperRequest.customerDisplayName -eq "Maria R.")
$acceptRequest = Invoke-DigibuddyApi POST "/api/v1/helper/bookings/$($remoteBooking.Data.summary.bookingId)/accept" $helper.accessToken
Confirm-Status "Helper accepts the real remote request" $acceptRequest
Confirm-Check "Accepting uses server booking state instead of helper-entered pay" (
    $acceptRequest.Data.summary.status -eq "AWAITING_CUSTOMER_APPROVAL" -and
    $acceptRequest.Data.summary.price.totalCents -eq 2900
)
$customerAccepted = Invoke-DigibuddyApi GET "/api/v1/customer/bookings/$($remoteBooking.Data.summary.bookingId)" $customer.accessToken
Confirm-Status "Customer sees the helper's accepted request" $customerAccepted
Confirm-Check "Customer can review the accepted platform price" ($customerAccepted.Data.summary.status -eq "AWAITING_CUSTOMER_APPROVAL")

$profileUpdate = Invoke-DigibuddyApi PUT "/api/v1/helper/application/profile" $helper.accessToken @{
    legalFirstName = "Charles"
    legalLastName = "Han"
    displayName = "Charles H."
    headline = "Patient help with computers, phones, and Wi-Fi"
    biography = "I help neighbors solve everyday technology problems using calm language and easy-to-follow steps."
    homeZip = $ZipCode
    serviceMode = "BOTH"
    serviceAreaSummary = "Crestview and nearby Okaloosa County communities"
    skillIds = @("windows", "home-networking", "phones")
    serviceCategoryIds = @("computer-help", "wifi-internet", "phone-tablet-help")
    yearsExperience = 7
    languages = @("en", "es")
    availabilitySummary = "Weekday evenings, Friday afternoons, and Saturday mornings"
}
Confirm-Status "Helper updates ZIP and all editable profile groups" $profileUpdate
Confirm-Check "Approved profile remains approved after editing" ($profileUpdate.Data.status -eq "APPROVED")
$updatedSearch = Invoke-DigibuddyApi GET "/api/v1/customer/helpers/search?zipCode=$ZipCode&remoteService=true&page=1&pageSize=50" $customer.accessToken
Confirm-Status "Customer refreshes updated helper information" $updatedSearch
Confirm-Check "Updated helper display name appears to customers" ($null -ne (@($updatedSearch.Data.items) | Where-Object displayName -eq "Charles H."))

[byte[]]$png = 137, 80, 78, 71, 13, 10, 26, 10
$photoGrant = Invoke-DigibuddyApi POST "/api/v1/helper/application/profile/photo/uploads" $helper.accessToken @{
    fileName = "profile.png"
    contentType = "image/png"
    sizeBytes = $png.Length
}
Confirm-Status "Create a helper photo upload from a selected file" $photoGrant
$photoUpload = Invoke-DigibuddyUpload $photoGrant.Data.uploadUrl $png "image/png"
Confirm-Status "Upload selected helper photo bytes" $photoUpload 204
$photoComplete = Invoke-DigibuddyApi POST "/api/v1/helper/application/profile/photo/complete" $helper.accessToken @{
    uploadId = $photoGrant.Data.uploadId
}
Confirm-Status "Complete helper profile photo update" $photoComplete
$photoStep = @($photoComplete.Data.steps) | Where-Object step -eq "PROFILE_MEDIA" | Select-Object -First 1
Confirm-Check "Helper profile stores the uploaded photo instead of a typed URL" ($photoStep.values.profilePictureUrl -match $photoGrant.Data.uploadId)

$privateBooking = Invoke-DigibuddyApi GET "/api/v1/customer/bookings/$($remoteBooking.Data.summary.bookingId)" $helper.accessToken
Confirm-Status "Unrelated account cannot read a customer's booking" $privateBooking 404

$conversations = Invoke-DigibuddyApi GET "/api/v1/customer/conversations" $customer.accessToken
Confirm-Status "Load customer conversations" $conversations
$welcome = @($conversations.Data.items) | Where-Object {
    $_.PSObject.Properties.Name -contains "canReply" -and $_.canReply -eq $false
} | Select-Object -First 1
Confirm-Check "Welcome conversation is labeled read-only" ($null -ne $welcome)
$welcomeMessages = Invoke-DigibuddyApi GET "/api/v1/customer/conversations/$($welcome.conversationId)/messages" $customer.accessToken
Confirm-Status "Open the welcome message" $welcomeMessages
$welcomeBody = (@($welcomeMessages.Data.items) | Select-Object -First 1).body
Confirm-Check "Welcome message includes the Digibuddy support number" (
    $welcomeBody -match '^Welcome to Digibuddy' -and $welcomeBody -match '\+1 \(312\) 555-0100'
)
$welcomeReply = Invoke-DigibuddyApi POST "/api/v1/customer/conversations/$($welcome.conversationId)/messages" $customer.accessToken @{
    clientMessageId = "welcome-reply-should-fail"
    body = "Can anyone see this?"
}
Confirm-Status "Welcome conversation rejects replies without crashing" $welcomeReply 403

$export = Invoke-DigibuddyApi POST "/api/v1/customer/privacy/data-export" $customer.accessToken
Confirm-Status "Request customer data export" $export
Confirm-Check "Data export request is queued" ($export.Data.status -eq "REQUESTED")

$email = "maria.rivera.32539@example.test"
$emailCredential = Invoke-DigibuddyApi PUT "/api/v1/auth/email-credential" $customer.accessToken @{
    email = $email
    password = "Correct-Horse-Battery-Staple-32539!"
}
Confirm-Status "Add an email and Argon2id password after onboarding" $emailCredential
$emailChallenge = Invoke-DigibuddyApi POST "/api/v1/auth/email/login" -Body @{
    email = $email
    password = "Correct-Horse-Battery-Staple-32539!"
}
Confirm-Status "Verify email and password as the first login step" $emailChallenge
Confirm-Check "Email login requires an SMS second factor" ($emailChallenge.Data.developmentCode -match '^\d{6}$')
$emailSession = Invoke-DigibuddyApi POST "/api/v1/auth/email/verify" -Body @{
    attemptId = $emailChallenge.Data.attemptId
    code = $emailChallenge.Data.developmentCode
    deviceId = "journey-email-second-factor"
    deviceName = "Email second-factor journey test"
}
Confirm-Status "Complete email login with SMS second factor" $emailSession

$deletion = Invoke-DigibuddyApi POST "/api/v1/customer/account/delete" $customer.accessToken @{
    confirmation = "DELETE"
}
Confirm-Status "Request account deletion with fresh authentication" $deletion
Confirm-Check "Account deletion request is recorded" ($deletion.Data.status -eq "DELETION_REQUESTED")
Confirm-Status "Deletion revokes the original customer session" (
    Invoke-DigibuddyApi GET "/api/v1/auth/me" $customer.accessToken
) 401
Confirm-Status "Deletion revokes the email/SMS customer session" (
    Invoke-DigibuddyApi GET "/api/v1/auth/me" $emailSession.Data.accessToken
) 401

$rotationSession = New-PhoneSession "850-555-0184" "journey-refresh-32539" "Refresh security journey test"
$rotated = Invoke-DigibuddyApi POST "/api/v1/auth/refresh" -Body @{
    refreshToken = $rotationSession.refreshToken
}
Confirm-Status "Rotate a refresh token" $rotated
Confirm-Check "Refresh rotation returns a different token" ($rotated.Data.refreshToken -ne $rotationSession.refreshToken)
$reuse = Invoke-DigibuddyApi POST "/api/v1/auth/refresh" -Body @{
    refreshToken = $rotationSession.refreshToken
}
Confirm-Status "Detect refresh-token reuse" $reuse 401
$revokedFamily = Invoke-DigibuddyApi GET "/api/v1/auth/me" $rotated.Data.accessToken
Confirm-Status "Refresh reuse revokes the affected session family" $revokedFamily 401

$allDevicesA = New-PhoneSession "850-555-0185" "journey-all-devices-a" "All-device logout test A"
$allDevicesB = New-PhoneSession "850-555-0185" "journey-all-devices-b" "All-device logout test B"
$logoutAll = Invoke-DigibuddyApi POST "/api/v1/auth/logout-all" $allDevicesA.accessToken
Confirm-Status "Sign an account out from all devices" $logoutAll
Confirm-Status "First access token is rejected after sign-out-all" (
    Invoke-DigibuddyApi GET "/api/v1/auth/me" $allDevicesA.accessToken
) 401
Confirm-Status "Second access token is rejected after sign-out-all" (
    Invoke-DigibuddyApi GET "/api/v1/auth/me" $allDevicesB.accessToken
) 401

$helperLogout = Invoke-DigibuddyApi POST "/api/v1/auth/logout" $helper.accessToken
Confirm-Status "Sign helper out from the current device" $helperLogout
$helperAfterLogout = Invoke-DigibuddyApi GET "/api/v1/auth/me" $helper.accessToken
Confirm-Status "Helper access token is rejected after logout" $helperAfterLogout 401

$passed = @($checks | Where-Object Passed).Count
[pscustomobject]@{
    ZipCode = $ZipCode
    Checks = $checks.Count
    Passed = $passed
    Failed = $checks.Count - $passed
    CustomerJourney = "phone signup -> onboarding -> settings -> helper search -> bookings -> chat safety -> data export -> email/SMS login -> deletion -> logout"
    HelperJourney = "phone signup -> onboarding -> review -> local approval -> discovery -> refresh requests -> accept -> shared chat -> edit profile -> file photo -> logout"
} | ConvertTo-Json -Depth 4
