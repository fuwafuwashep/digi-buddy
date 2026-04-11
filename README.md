# DigiBuddy

A two-sided marketplace connecting customers with **digital helpers** — tech support people who can help with phones, computers, WiFi, smart home devices, and more. Think DoorDash, but for tech support.

## Project Structure

```
Digibuddy/
├── app/                  # Customer Android app  (com.digibuddy.customer)
├── helper-app/           # Helper Android app    (com.digibuddy.helper)
├── core/                 # Shared library (models, API service, socket)
└── server/               # Node.js + Express backend
```

## Tech Stack

| Layer | Technology |
|---|---|
| Android | Kotlin + Jetpack Compose + Material 3 |
| DI | Hilt + KSP |
| Networking | Retrofit + OkHttp |
| Real-time | Socket.io |
| Maps | Google Maps Compose |
| Location | FusedLocationProvider |
| Storage | DataStore Preferences |
| Backend | Node.js + Express + TypeScript |
| Database | PostgreSQL + Prisma ORM |
| Auth | JWT (access + refresh) + OTP (SMS via Twilio) |

---

## Prerequisites

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 17+**
- **Node.js 20+** and **npm**
- **PostgreSQL 15+** (local or hosted)
- **Google Cloud account** for Maps API key

---

## 1 — Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials.
2. Create an API key and enable:
   - **Maps SDK for Android**
   - **Geocoding API** (optional, for address lookup)
3. Open `secrets.properties` in the project root and replace the placeholder:
   ```
   MAPS_API_KEY=YOUR_ACTUAL_KEY_HERE
   ```
   > `secrets.properties` is gitignored — never commit your real key.

---

## 2 — Server Setup

### 2a. Install dependencies
```bash
cd server
npm install
```

### 2b. Configure environment
```bash
cp .env.example .env
```

Edit `.env`:
```env
DATABASE_URL="postgresql://USER:PASSWORD@localhost:5432/digibuddy"
JWT_SECRET="change-me-to-a-long-random-string"
JWT_REFRESH_SECRET="another-long-random-string"
PORT=3000

# Optional: Twilio for real SMS OTP. Leave blank to use console output in dev.
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_PHONE_NUMBER=
```

### 2c. Set up the database
```bash
# Run migrations
npm run db:migrate

# (Optional) Seed with sample helpers
npm run db:seed
```

### 2d. Start the server
```bash
# Development (hot reload)
npm run dev

# Production
npm run build && npm start
```

The API is available at `http://localhost:3000/api/`.

---

## 3 — Android Setup

### 3a. Open in Android Studio
Open the **root** `Digibuddy/` folder in Android Studio (not a sub-folder).

### 3b. Sync Gradle
Click **Sync Now** when prompted, or go to **File → Sync Project with Gradle Files**.

### 3c. Configure the server URL
In `core/src/main/java/com/digibuddy/core/utils/Constants.kt`, update `BASE_URL` if needed:

| Environment | URL |
|---|---|
| Android Emulator | `http://10.0.2.2:3000/api/` (default) |
| Physical device on same WiFi | `http://YOUR_MACHINE_IP:3000/api/` |
| Production | `https://api.yourserver.com/api/` |

### 3d. Run the apps
- **Customer app** → select the `:app` run configuration
- **Helper app** → select the `:helper-app` run configuration

> You can run both simultaneously on different emulators/devices.

---

## 4 — App Flow

### Customer App
```
Splash → Permissions → Login (phone OTP or email)
  → Home (nearby helpers list)
  → Map (helpers on Google Maps)
  → Search (helpers in a specified area)
  → Helper Detail (profile, ratings, book)
  → Chat (coordinate meetup location in real time)
  → Bookings (history, status)
  → Profile (account settings, logout)
```

### Helper App
```
Splash → Permissions → Login → Complete Profile
  → Dashboard (toggle availability, incoming bookings)
  → Set Work Location (public address + radius on map)
  → Chat (reply to customers)
  → Profile (bio, skills, avg rating, sessions count)
```

---

## 5 — Key Features

- **Phone OTP or email** login for both apps
- **Real-time chat** via Socket.io with typing indicators
- **Google Maps** — customers see helper markers; helpers pick their public work spot on the map
- **Haversine distance filter** — only helpers within the requested radius appear
- **5-star ratings** with written reviews
- **Booking lifecycle** — PENDING → ACCEPTED → IN_PROGRESS → COMPLETED
- **Availability toggle** — helpers go on/off duty from their dashboard

---

## 6 — Development Tips

### OTP in development
When `TWILIO_*` env vars are not set, the server logs OTP codes to the console:
```
[OTP] User <userId>: 482910
```

### Prisma Studio (database GUI)
```bash
cd server
npm run db:studio
```

### Reset and reseed the database
```bash
cd server
npx prisma migrate reset   # drops, recreates, and re-seeds
```

---

## 7 — Project Architecture

### Android (Clean Architecture / MVVM)
```
core/
  model/     → shared data classes (User, Booking, ChatMessage…)
  network/   → Retrofit ApiService + AuthInterceptor
  socket/    → SocketManager singleton

app/ (Customer)
  data/
    local/   → UserPreferences (DataStore)
    repository/ → AuthRepository, HelperRepository, BookingRepository, ChatRepository
  di/        → Hilt AppModule
  ui/
    screens/ → one package per screen (Screen + ViewModel)
    components/ → StarRating, HelperCard, BottomNavBar
    theme/   → Color, Theme, Type
  navigation/ → Screen sealed class + NavHost
```

### Server (REST + Socket.io)
```
server/src/
  routes/       → auth, helpers, bookings, ratings, chat, users
  controllers/  → business logic handlers
  middleware/   → JWT auth, requireRole
  socket/       → Socket.io event handlers
  utils/        → prisma, jwt, otp, geo (haversine), logger
prisma/
  schema.prisma → database models
  seed.ts       → sample data
```

---

## 8 — API Reference (summary)

### Auth
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/send-otp` | Send OTP to phone |
| POST | `/api/auth/verify-otp` | Verify OTP, returns tokens |
| POST | `/api/auth/register` | Email registration |
| POST | `/api/auth/login` | Email login |
| POST | `/api/auth/refresh` | Refresh access token |
| POST | `/api/auth/complete-profile` | Set name + role after OTP |

### Helpers
| Method | Path | Description |
|---|---|---|
| GET | `/api/helpers/nearby?lat=&lng=&radius=` | Helpers within radius |
| GET | `/api/helpers/search?lat=&lng=&radius=&skills=` | Search helpers |
| GET | `/api/helpers/:id` | Helper profile + ratings |
| PATCH | `/api/helpers/profile` | Update bio/skills/rate |
| PATCH | `/api/helpers/availability` | Toggle isAvailable |
| PATCH | `/api/helpers/work-location` | Set work address + coords |

### Bookings
| Method | Path | Description |
|---|---|---|
| POST | `/api/bookings` | Create booking |
| GET | `/api/bookings?role=helper` | My bookings (as helper) |
| GET | `/api/bookings/:id` | Single booking |
| PATCH | `/api/bookings/:id/status` | Update status |

### Chat
| Method | Path | Description |
|---|---|---|
| GET | `/api/chat/rooms` | My chat rooms |
| GET | `/api/chat/rooms/:roomId/messages` | Messages (paginated) |

### Ratings
| Method | Path | Description |
|---|---|---|
| GET | `/api/ratings/helper/:helperId` | Helper ratings |
| POST | `/api/ratings` | Submit rating |

---

## License

MIT
