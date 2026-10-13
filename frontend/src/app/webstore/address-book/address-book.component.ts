import { MatIconModule } from '@angular/material/icon';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatExpansionModule } from '@angular/material/expansion';
import { AddressService } from '../../core/services/address.service';
import { Address } from '../../core/models/models';

@Component({
  selector: 'app-address-book',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, FormsModule, ReactiveFormsModule, MatSnackBarModule, MatExpansionModule],
  templateUrl: './address-book.component.html',
  styleUrls: ['./address-book.component.scss']
})
export class AddressBookComponent implements OnInit {
  private addressService = inject(AddressService);
  private fb = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);

  addresses: Address[] = [];
  defaultAddress: Address | null = null;
  isLoading = true;

  addressForm!: FormGroup;

  ngOnInit(): void {
    this.initForm();
    this.loadAddresses();
  }

  initForm(): void {
    this.addressForm = this.fb.group({
      label: ['Home', Validators.required],
      full_name: ['', Validators.required],
      phone: [''],
      address_line: ['', Validators.required],
      city: [''],
      province: [''],
      postal_code: ['']
    });
  }

  loadAddresses(): void {
    this.isLoading = true;
    this.addressService.getAddresses().subscribe({
      next: (data) => {
        this.addresses = data || [];
        this.defaultAddress = this.addresses.find(a => Boolean(a.is_default)) || null;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  onSaveAddress(): void {
    if (this.addressForm.invalid) return;

    this.addressService.addAddress(this.addressForm.value).subscribe({
      next: () => {
        this.snackBar.open('Address saved successfully!', 'Close', { duration: 3000 });
        this.addressForm.reset({ label: 'Home' });
        this.loadAddresses();
      },
      error: () => {
        this.snackBar.open('Failed to save address', 'Close', { duration: 3000 });
      }
    });
  }

  setDefault(address: Address): void {
    this.addressService.setDefaultAddress(address.address_id).subscribe({
      next: () => {
        this.snackBar.open('Default address updated', 'Close', { duration: 2500 });
        this.loadAddresses();
      },
      error: () => {
        this.snackBar.open('Failed to update default address', 'Close', { duration: 2500 });
      }
    });
  }

  deleteAddress(address: Address): void {
    if (confirm('Are you sure you want to delete this address?')) {
      this.addressService.deleteAddress(address.address_id).subscribe({
        next: () => {
          this.snackBar.open('Address deleted', 'Close', { duration: 2500 });
          this.loadAddresses();
        },
        error: () => {
          this.snackBar.open('Failed to delete address', 'Close', { duration: 2500 });
        }
      });
    }
  }
}
