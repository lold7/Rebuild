import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { apiErrorMessage } from '../../core/utils/api.utils';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, ReactiveFormsModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss']
})
export class RegisterComponent {
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private router = inject(Router);

  registerForm: FormGroup = this.fb.group({
    username: ['', Validators.required],
    phone: [''],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  errorMessage = '';
  isLoading = false;

  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.errorMessage = this.registerForm.get('password')?.hasError('minlength')
        ? 'Password must be at least 8 characters.'
        : 'Please fill in your username, email and password.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.register(this.registerForm.value).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.status === 409
          ? 'This email is already registered.'
          : apiErrorMessage(err, 'Registration failed. Please check your details.');
      }
    });
  }
}
