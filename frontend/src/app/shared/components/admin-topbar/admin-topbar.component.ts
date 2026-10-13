import { MatIconModule } from '@angular/material/icon';
import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-admin-topbar',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, MatMenuModule, MatButtonModule, MatDividerModule],
  templateUrl: './admin-topbar.component.html',
  styleUrls: ['./admin-topbar.component.scss']
})
export class AdminTopbarComponent {
  authService = inject(AuthService);

  @Input() title: string = 'Overview';

  logout(): void {
    this.authService.logout();
  }
}
