import { MatIconModule } from '@angular/material/icon';
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatExpansionModule } from '@angular/material/expansion';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule, FormsModule, MatSnackBarModule, MatExpansionModule],
  templateUrl: './contact.component.html',
  styleUrls: ['./contact.component.scss']
})
export class ContactComponent {
  private snackBar = inject(MatSnackBar);

  formData = {
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    subject: 'General Inquiry',
    message: ''
  };

  onSubmit(): void {
    this.snackBar.open('Thank you for contacting us! We will respond within 24 hours.', 'Close', { duration: 4000 });
    this.formData = {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      subject: 'General Inquiry',
      message: ''
    };
  }

  scrollToTop(): void {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
