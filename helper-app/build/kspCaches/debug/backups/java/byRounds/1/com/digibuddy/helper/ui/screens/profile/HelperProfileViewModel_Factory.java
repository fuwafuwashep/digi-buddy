package com.digibuddy.helper.ui.screens.profile;

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
public final class HelperProfileViewModel_Factory implements Factory<HelperProfileViewModel> {
  private final Provider<HelperPreferences> helperPreferencesProvider;

  private final Provider<ApiService> apiServiceProvider;

  public HelperProfileViewModel_Factory(Provider<HelperPreferences> helperPreferencesProvider,
      Provider<ApiService> apiServiceProvider) {
    this.helperPreferencesProvider = helperPreferencesProvider;
    this.apiServiceProvider = apiServiceProvider;
  }

  @Override
  public HelperProfileViewModel get() {
    return newInstance(helperPreferencesProvider.get(), apiServiceProvider.get());
  }

  public static HelperProfileViewModel_Factory create(
      Provider<HelperPreferences> helperPreferencesProvider,
      Provider<ApiService> apiServiceProvider) {
    return new HelperProfileViewModel_Factory(helperPreferencesProvider, apiServiceProvider);
  }

  public static HelperProfileViewModel newInstance(HelperPreferences helperPreferences,
      ApiService apiService) {
    return new HelperProfileViewModel(helperPreferences, apiService);
  }
}
