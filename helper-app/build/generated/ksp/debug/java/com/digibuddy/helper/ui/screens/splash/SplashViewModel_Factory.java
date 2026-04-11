package com.digibuddy.helper.ui.screens.splash;

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
public final class SplashViewModel_Factory implements Factory<SplashViewModel> {
  private final Provider<HelperPreferences> helperPreferencesProvider;

  public SplashViewModel_Factory(Provider<HelperPreferences> helperPreferencesProvider) {
    this.helperPreferencesProvider = helperPreferencesProvider;
  }

  @Override
  public SplashViewModel get() {
    return newInstance(helperPreferencesProvider.get());
  }

  public static SplashViewModel_Factory create(
      Provider<HelperPreferences> helperPreferencesProvider) {
    return new SplashViewModel_Factory(helperPreferencesProvider);
  }

  public static SplashViewModel newInstance(HelperPreferences helperPreferences) {
    return new SplashViewModel(helperPreferences);
  }
}
