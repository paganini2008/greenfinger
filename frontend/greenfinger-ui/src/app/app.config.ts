import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  MAT_FORM_FIELD_DEFAULT_OPTIONS,
  MatFormFieldDefaultOptions,
} from '@angular/material/form-field';
import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  inject,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      // route params arrive as component inputs, so a page reads :name without an ActivatedRoute
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor])),
    // The guards run before the first render, so the session has to be settled before they do --
    // otherwise a reload on any page bounces to the login form and back again.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).restore())),
    // A form field reserves one line under it for the hint and no more, so a hint that wraps
    // runs into whatever is below -- on the catalog form, "Narrows a search and groups the list.
    // Nothing fits? Pick other." landed on top of the Start url box. Dynamic lets that area grow
    // to whatever the hint needs. Set once here rather than per field, because the next long
    // hint somebody writes should not have to rediscover this.
    {
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: { subscriptSizing: 'dynamic' } as MatFormFieldDefaultOptions,
    },
  ],
};
