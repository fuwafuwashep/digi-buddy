package com.digibuddy.helper.ui.screens.dashboard;

import android.content.Context;
import com.digibuddy.core.network.ApiService;
import com.digibuddy.helper.data.local.HelperPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<ApiService> apiServiceProvider;

  private final Provider<HelperPreferences> helperPreferencesProvider;

  public DashboardViewModel_Factory(Provider<Context> contextProvider,
      Provider<ApiService> apiServiceProvider,
      Provider<HelperPreferences> helperPreferencesProvider) {
    this.contextProvider = contextProvider;
    this.apiServiceProvider = apiServiceProvider;
    this.helperPreferencesProvider = helperPreferencesProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(contextProvider.get(), apiServiceProvider.get(), helperPreferencesProvider.get());
  }

  public static DashboardViewModel_Factory create(Provider<Context> contextProvider,
      Provider<ApiService> apiServiceProvider,
      Provider<HelperPreferences> helperPreferencesProvider) {
    return new DashboardViewModel_Factory(contextProvider, apiServiceProvider, helperPreferencesProvider);
  }

  public static DashboardViewModel newInstance(Context context, ApiService apiService,
      HelperPreferences helperPreferences) {
    return new DashboardViewModel(context, apiService, helperPreferences);
  }
}
