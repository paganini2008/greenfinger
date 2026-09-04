import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './core/auth.guard';

/**
 * The pages, and who may reach them.
 *
 * Lazily loaded one by one: the login page is the only thing a signed-out visitor can see, and
 * making them download the catalog editor to look at it would be paying for a page they may never
 * be allowed to open.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'catalogs' },
  {
    path: 'login',
    title: 'Sign in - Greenfinger',
    loadComponent: () => import('./pages/login/login').then((m) => m.LoginPage),
  },
  {
    path: 'catalogs',
    title: 'Catalogs - Greenfinger',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/catalogs/catalogs').then((m) => m.CatalogsPage),
  },
  {
    path: 'catalogs/new',
    title: 'New catalog - Greenfinger',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/catalog-edit/catalog-edit').then((m) => m.CatalogEditPage),
  },
  {
    path: 'catalogs/:ref/edit',
    title: 'Edit catalog - Greenfinger',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/catalog-edit/catalog-edit').then((m) => m.CatalogEditPage),
  },
  {
    path: 'catalogs/:ref/monitor',
    title: 'Monitor - Greenfinger',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/monitor/monitor').then((m) => m.MonitorPage),
  },
  {
    path: 'search',
    title: 'Search - Greenfinger',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/search/search').then((m) => m.SearchPage),
  },
  {
    path: 'cluster',
    title: 'Cluster - Greenfinger',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/cluster/cluster').then((m) => m.ClusterPage),
  },
  {
    path: 'about',
    title: 'About - Greenfinger',
    loadComponent: () => import('./pages/about/about').then((m) => m.AboutPage),
  },
  { path: '**', redirectTo: 'catalogs' },
];
