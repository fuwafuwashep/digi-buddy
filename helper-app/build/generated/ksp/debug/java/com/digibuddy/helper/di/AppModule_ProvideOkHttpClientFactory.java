package com.digibuddy.helper.di;

import com.digibuddy.helper.data.local.HelperPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AppModule_ProvideOkHttpClientFactory implements Factory<OkHttpClient> {
  private final Provider<HelperPreferences> helperPreferencesProvider;

  public AppModule_ProvideOkHttpClientFactory(
      Provider<HelperPreferences> helperPreferencesProvider) {
    this.helperPreferencesProvider = helperPreferencesProvider;
  }

  @Override
  public OkHttpClient get() {
    return provideOkHttpClient(helperPreferencesProvider.get());
  }

  public static AppModule_ProvideOkHttpClientFactory create(
      Provider<HelperPreferences> helperPreferencesProvider) {
    return new AppModule_ProvideOkHttpClientFactory(helperPreferencesProvider);
  }

  public static OkHttpClient provideOkHttpClient(HelperPreferences helperPreferences) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideOkHttpClient(helperPreferences));
  }
}
