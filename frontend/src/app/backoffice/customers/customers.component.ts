import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { User } from '../../core/models/models';
import { AdminSidebarComponent } from '../../shared/components/admin-sidebar/admin-sidebar.component';
import { AdminTopbarComponent } from '../../shared/components/admin-topbar/admin-topbar.component';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, FormsModule, AdminSidebarComponent, AdminTopbarComponent],
  templateUrl: './customers.component.html',
  styleUrls: ['./customers.component.scss']
})
export class CustomersComponent implements OnInit {
  private adminService = inject(AdminService);

  customers: any[] = [];
  searchQuery = '';
  isLoading = true;

  ngOnInit(): void {
    this.loadCustomers();
  }

  loadCustomers(): void {
    this.isLoading = true;
    this.adminService.getCustomers().subscribe({
      next: (data) => {
        this.customers = data || [];
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  get filteredCustomers(): any[] {
    if (!this.searchQuery.trim()) return this.customers;
    const q = this.searchQuery.toLowerCase();
    return this.customers.filter(c =>
      (c.username && c.username.toLowerCase().includes(q)) ||
      (c.email && c.email.toLowerCase().includes(q))
    );
  }

  getInitials(name?: string): string {
    if (!name) return '??';
    return name.split(' ').map(w => w.charAt(0).toUpperCase()).join('').substring(0, 2);
  }
}
