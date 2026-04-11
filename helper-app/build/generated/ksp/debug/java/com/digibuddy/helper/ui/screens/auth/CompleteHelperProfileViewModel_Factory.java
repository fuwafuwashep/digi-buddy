package com.digibuddy.helper.ui.screens.auth;

import com.digibuddy.core.network.ApiService;
import com.digibuddy.helper.data.local.HelperPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class CompleteHelperProfileViewModel_Factory implements Factory<CompleteHelperProfileViewModel> {
  private final Provider<ApiService> apiServiceProvider;

  private final Provider<HelperPreferences> helperPreferencesProvider;

  public CompleteHelperProfileViewModel_Factory(Provider<ApiService> apiServiceProvider,
      Provider<HelperPreferences> helperPreferencesProvider) {
    this.apiServiceProvider = apiServiceProvider;
    this.helperPreferencesProvider = helperPreferencesProvider;
  }

  @Override
  public CompleteHelperProfileViewModel get() {
    return newInstance(apiServiceProvider.get(), helperPreferencesProvider.get());
  }

  public static CompleteHelperProfileViewModel_Factory create(
      Provider<ApiService> apiServiceProvider,
      Provider<HelperPreferences> helperPreferencesProvider) {
    return new CompleteHelperProfileViewModel_Factory(apiServiceProvider, helperPreferencesProvider);
  }

  public static CompleteHelperProfileViewModel newInstance(ApiService apiService,
      HelperPreferences helperPreferences) {
    return new CompleteHelperProfileViewModel(apiService, helperPreferences);
  }
}
