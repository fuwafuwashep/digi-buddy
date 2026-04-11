package com.digibuddy.helper;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.digibuddy.core.network.ApiService;
import com.digibuddy.helper.data.local.HelperPreferences;
import com.digibuddy.helper.di.AppModule_ProvideApiServiceFactory;
import com.digibuddy.helper.di.AppModule_ProvideOkHttpClientFactory;
import com.digibuddy.helper.di.AppModule_ProvideRetrofitFactory;
import com.digibuddy.helper.ui.screens.auth.CompleteHelperProfileViewModel;
import com.digibuddy.helper.ui.screens.auth.CompleteHelperProfileViewModel_HiltModules;
import com.digibuddy.helper.ui.screens.auth.HelperLoginViewModel;
import com.digibuddy.helper.ui.screens.auth.HelperLoginViewModel_HiltModules;
import com.digibuddy.helper.ui.screens.auth.HelperOtpViewModel;
import com.digibuddy.helper.ui.screens.auth.HelperOtpViewModel_HiltModules;
import com.digibuddy.helper.ui.screens.chat.HelperChatViewModel;
import com.digibuddy.helper.ui.screens.chat.HelperChatViewModel_HiltModules;
import com.digibuddy.helper.ui.screens.dashboard.DashboardViewModel;
import com.digibuddy.helper.ui.screens.dashboard.DashboardViewModel_HiltModules;
import com.digibuddy.helper.ui.screens.location.WorkLocationViewModel;
import com.digibuddy.helper.ui.screens.location.WorkLocationViewModel_HiltModules;
import com.digibuddy.helper.ui.screens.profile.HelperProfileViewModel;
import com.digibuddy.helper.ui.screens.profile.HelperProfileViewModel_HiltModules;
import com.digibuddy.helper.ui.screens.splash.SplashViewModel;
import com.digibuddy.helper.ui.screens.splash.SplashViewModel_HiltModules;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.IdentifierNameString;
import dagger.internal.KeepFieldType;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

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
public final class DaggerDigiBuddyHelperApp_HiltComponents_SingletonC {
  private DaggerDigiBuddyHelperApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public DigiBuddyHelperApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements DigiBuddyHelperApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public DigiBuddyHelperApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements DigiBuddyHelperApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public DigiBuddyHelperApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements DigiBuddyHelperApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public DigiBuddyHelperApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements DigiBuddyHelperApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public DigiBuddyHelperApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements DigiBuddyHelperApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public DigiBuddyHelperApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements DigiBuddyHelperApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public DigiBuddyHelperApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements DigiBuddyHelperApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public DigiBuddyHelperApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends DigiBuddyHelperApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends DigiBuddyHelperApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends DigiBuddyHelperApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends DigiBuddyHelperApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity arg0) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(8).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_auth_CompleteHelperProfileViewModel, CompleteHelperProfileViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_dashboard_DashboardViewModel, DashboardViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_chat_HelperChatViewModel, HelperChatViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_auth_HelperLoginViewModel, HelperLoginViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_auth_HelperOtpViewModel, HelperOtpViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_profile_HelperProfileViewModel, HelperProfileViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_splash_SplashViewModel, SplashViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_location_WorkLocationViewModel, WorkLocationViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_digibuddy_helper_ui_screens_location_WorkLocationViewModel = "com.digibuddy.helper.ui.screens.location.WorkLocationViewModel";

      static String com_digibuddy_helper_ui_screens_auth_HelperLoginViewModel = "com.digibuddy.helper.ui.screens.auth.HelperLoginViewModel";

      static String com_digibuddy_helper_ui_screens_dashboard_DashboardViewModel = "com.digibuddy.helper.ui.screens.dashboard.DashboardViewModel";

      static String com_digibuddy_helper_ui_screens_profile_HelperProfileViewModel = "com.digibuddy.helper.ui.screens.profile.HelperProfileViewModel";

      static String com_digibuddy_helper_ui_screens_splash_SplashViewModel = "com.digibuddy.helper.ui.screens.splash.SplashViewModel";

      static String com_digibuddy_helper_ui_screens_auth_HelperOtpViewModel = "com.digibuddy.helper.ui.screens.auth.HelperOtpViewModel";

      static String com_digibuddy_helper_ui_screens_chat_HelperChatViewModel = "com.digibuddy.helper.ui.screens.chat.HelperChatViewModel";

      static String com_digibuddy_helper_ui_screens_auth_CompleteHelperProfileViewModel = "com.digibuddy.helper.ui.screens.auth.CompleteHelperProfileViewModel";

      @KeepFieldType
      WorkLocationViewModel com_digibuddy_helper_ui_screens_location_WorkLocationViewModel2;

      @KeepFieldType
      HelperLoginViewModel com_digibuddy_helper_ui_screens_auth_HelperLoginViewModel2;

      @KeepFieldType
      DashboardViewModel com_digibuddy_helper_ui_screens_dashboard_DashboardViewModel2;

      @KeepFieldType
      HelperProfileViewModel com_digibuddy_helper_ui_screens_profile_HelperProfileViewModel2;

      @KeepFieldType
      SplashViewModel com_digibuddy_helper_ui_screens_splash_SplashViewModel2;

      @KeepFieldType
      HelperOtpViewModel com_digibuddy_helper_ui_screens_auth_HelperOtpViewModel2;

      @KeepFieldType
      HelperChatViewModel com_digibuddy_helper_ui_screens_chat_HelperChatViewModel2;

      @KeepFieldType
      CompleteHelperProfileViewModel com_digibuddy_helper_ui_screens_auth_CompleteHelperProfileViewModel2;
    }
  }

  private static final class ViewModelCImpl extends DigiBuddyHelperApp_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<CompleteHelperProfileViewModel> completeHelperProfileViewModelProvider;

    private Provider<DashboardViewModel> dashboardViewModelProvider;

    private Provider<HelperChatViewModel> helperChatViewModelProvider;

    private Provider<HelperLoginViewModel> helperLoginViewModelProvider;

    private Provider<HelperOtpViewModel> helperOtpViewModelProvider;

    private Provider<HelperProfileViewModel> helperProfileViewModelProvider;

    private Provider<SplashViewModel> splashViewModelProvider;

    private Provider<WorkLocationViewModel> workLocationViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.completeHelperProfileViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.dashboardViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.helperChatViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.helperLoginViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.helperOtpViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.helperProfileViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.splashViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.workLocationViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(8).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_auth_CompleteHelperProfileViewModel, ((Provider) completeHelperProfileViewModelProvider)).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_dashboard_DashboardViewModel, ((Provider) dashboardViewModelProvider)).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_chat_HelperChatViewModel, ((Provider) helperChatViewModelProvider)).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_auth_HelperLoginViewModel, ((Provider) helperLoginViewModelProvider)).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_auth_HelperOtpViewModel, ((Provider) helperOtpViewModelProvider)).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_profile_HelperProfileViewModel, ((Provider) helperProfileViewModelProvider)).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_splash_SplashViewModel, ((Provider) splashViewModelProvider)).put(LazyClassKeyProvider.com_digibuddy_helper_ui_screens_location_WorkLocationViewModel, ((Provider) workLocationViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_digibuddy_helper_ui_screens_auth_CompleteHelperProfileViewModel = "com.digibuddy.helper.ui.screens.auth.CompleteHelperProfileViewModel";

      static String com_digibuddy_helper_ui_screens_auth_HelperLoginViewModel = "com.digibuddy.helper.ui.screens.auth.HelperLoginViewModel";

      static String com_digibuddy_helper_ui_screens_profile_HelperProfileViewModel = "com.digibuddy.helper.ui.screens.profile.HelperProfileViewModel";

      static String com_digibuddy_helper_ui_screens_dashboard_DashboardViewModel = "com.digibuddy.helper.ui.screens.dashboard.DashboardViewModel";

      static String com_digibuddy_helper_ui_screens_auth_HelperOtpViewModel = "com.digibuddy.helper.ui.screens.auth.HelperOtpViewModel";

      static String com_digibuddy_helper_ui_screens_chat_HelperChatViewModel = "com.digibuddy.helper.ui.screens.chat.HelperChatViewModel";

      static String com_digibuddy_helper_ui_screens_splash_SplashViewModel = "com.digibuddy.helper.ui.screens.splash.SplashViewModel";

      static String com_digibuddy_helper_ui_screens_location_WorkLocationViewModel = "com.digibuddy.helper.ui.screens.location.WorkLocationViewModel";

      @KeepFieldType
      CompleteHelperProfileViewModel com_digibuddy_helper_ui_screens_auth_CompleteHelperProfileViewModel2;

      @KeepFieldType
      HelperLoginViewModel com_digibuddy_helper_ui_screens_auth_HelperLoginViewModel2;

      @KeepFieldType
      HelperProfileViewModel com_digibuddy_helper_ui_screens_profile_HelperProfileViewModel2;

      @KeepFieldType
      DashboardViewModel com_digibuddy_helper_ui_screens_dashboard_DashboardViewModel2;

      @KeepFieldType
      HelperOtpViewModel com_digibuddy_helper_ui_screens_auth_HelperOtpViewModel2;

      @KeepFieldType
      HelperChatViewModel com_digibuddy_helper_ui_screens_chat_HelperChatViewModel2;

      @KeepFieldType
      SplashViewModel com_digibuddy_helper_ui_screens_splash_SplashViewModel2;

      @KeepFieldType
      WorkLocationViewModel com_digibuddy_helper_ui_screens_location_WorkLocationViewModel2;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.digibuddy.helper.ui.screens.auth.CompleteHelperProfileViewModel 
          return (T) new CompleteHelperProfileViewModel(singletonCImpl.provideApiServiceProvider.get(), singletonCImpl.helperPreferencesProvider.get());

          case 1: // com.digibuddy.helper.ui.screens.dashboard.DashboardViewModel 
          return (T) new DashboardViewModel(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideApiServiceProvider.get(), singletonCImpl.helperPreferencesProvider.get());

          case 2: // com.digibuddy.helper.ui.screens.chat.HelperChatViewModel 
          return (T) new HelperChatViewModel(singletonCImpl.provideApiServiceProvider.get(), singletonCImpl.helperPreferencesProvider.get());

          case 3: // com.digibuddy.helper.ui.screens.auth.HelperLoginViewModel 
          return (T) new HelperLoginViewModel(singletonCImpl.provideApiServiceProvider.get(), singletonCImpl.helperPreferencesProvider.get());

          case 4: // com.digibuddy.helper.ui.screens.auth.HelperOtpViewModel 
          return (T) new HelperOtpViewModel(singletonCImpl.provideApiServiceProvider.get(), singletonCImpl.helperPreferencesProvider.get());

          case 5: // com.digibuddy.helper.ui.screens.profile.HelperProfileViewModel 
          return (T) new HelperProfileViewModel(singletonCImpl.helperPreferencesProvider.get(), singletonCImpl.provideApiServiceProvider.get());

          case 6: // com.digibuddy.helper.ui.screens.splash.SplashViewModel 
          return (T) new SplashViewModel(singletonCImpl.helperPreferencesProvider.get());

          case 7: // com.digibuddy.helper.ui.screens.location.WorkLocationViewModel 
          return (T) new WorkLocationViewModel(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideApiServiceProvider.get(), singletonCImpl.helperPreferencesProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends DigiBuddyHelperApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends DigiBuddyHelperApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends DigiBuddyHelperApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<HelperPreferences> helperPreferencesProvider;

    private Provider<OkHttpClient> provideOkHttpClientProvider;

    private Provider<Retrofit> provideRetrofitProvider;

    private Provider<ApiService> provideApiServiceProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.helperPreferencesProvider = DoubleCheck.provider(new SwitchingProvider<HelperPreferences>(singletonCImpl, 3));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 2));
      this.provideRetrofitProvider = DoubleCheck.provider(new SwitchingProvider<Retrofit>(singletonCImpl, 1));
      this.provideApiServiceProvider = DoubleCheck.provider(new SwitchingProvider<ApiService>(singletonCImpl, 0));
    }

    @Override
    public void injectDigiBuddyHelperApp(DigiBuddyHelperApp digiBuddyHelperApp) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.digibuddy.core.network.ApiService 
          return (T) AppModule_ProvideApiServiceFactory.provideApiService(singletonCImpl.provideRetrofitProvider.get());

          case 1: // retrofit2.Retrofit 
          return (T) AppModule_ProvideRetrofitFactory.provideRetrofit(singletonCImpl.provideOkHttpClientProvider.get());

          case 2: // okhttp3.OkHttpClient 
          return (T) AppModule_ProvideOkHttpClientFactory.provideOkHttpClient(singletonCImpl.helperPreferencesProvider.get());

          case 3: // com.digibuddy.helper.data.local.HelperPreferences 
          return (T) new HelperPreferences(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
