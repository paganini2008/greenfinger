import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

/**
 * What this is and what the words on the other pages mean.
 *
 * Reachable signed out, because somebody handed a link to the login page should be able to find
 * out what they have been handed.
 */
@Component({
  selector: 'gf-about',
  imports: [RouterLink, MatIconModule, MatButtonModule],
  templateUrl: './about.html',
  styleUrl: './about.scss',
})
export class AboutPage {
  /** The version the server reports, not one written into this page. */
  protected readonly version = signal('');

  constructor() {
    inject(ApiService)
      .version()
      .subscribe({ next: (server) => this.version.set(server.version), error: () => undefined });
  }

  protected readonly concepts = [
    {
      icon: 'travel_explore',
      title: 'Crawl',
      body: 'Starts at the seed url and follows links that stay under it. Never leaves the site: a crawl of a.com does not wander onto b.com.',
    },
    {
      icon: 'sync',
      title: 'Update',
      body: 'Carries on in the same version from where the last run stopped, keeping the url filters. Interrupted runs resume this way.',
    },
    {
      icon: 'restart_alt',
      title: 'Rebuild',
      body: 'Version + 1, and nothing is deleted. The previous version stays searchable until the new one finishes.',
    },
    {
      icon: 'replay',
      title: 'Replay',
      body: 'Rebuilds the index and the vectors from the files already on disk. No pages are fetched again.',
    },
    {
      icon: 'layers',
      title: 'Versions',
      body: 'Every layer -- database, files, index, vectors -- keeps every version. Deleting is a separate, deliberate operation.',
    },
    {
      icon: 'search',
      title: 'Search',
      body: 'Never touches the database. Words go to Elasticsearch, meaning and pictures to the vector store, and both carry their own metadata.',
    },
  ];
}
