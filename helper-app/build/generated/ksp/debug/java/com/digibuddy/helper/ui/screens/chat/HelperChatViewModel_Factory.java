package com.digibuddy.helper.ui.screens.chat;

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
public final class HelperChatViewModel_Factory implements Factory<HelperChatViewModel> {
  private final Provider<ApiService> apiServiceProvider;

  private final Provider<HelperPreferences> helperPreferencesProvider;

  public HelperChatViewModel_Factory(Provider<ApiService> apiServiceProvider,
      Provider<HelperPreferences> helperPreferencesProvider) {
    this.apiServiceProvider = apiServiceProvider;
    this.helperPreferencesProvider = helperPreferencesProvider;
  }

  @Override
  public HelperChatViewModel get() {
    return newInstance(apiServiceProvider.get(), helperPreferencesProvider.get());
  }

  public static HelperChatViewModel_Factory create(Provider<ApiService> apiServiceProvider,
      Provider<HelperPreferences> helperPreferencesProvider) {
    return new HelperChatViewModel_Factory(apiServiceProvider, helperPreferencesProvider);
  }

  public static HelperChatViewModel newInstance(ApiService apiService,
      HelperPreferences helperPreferences) {
    return new HelperChatViewModel(apiService, helperPreferences);
  }
}
