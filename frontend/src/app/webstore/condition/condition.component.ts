import { MatIconModule } from '@angular/material/icon';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-condition',
  standalone: true,
  imports: [
    MatIconModule,CommonModule, RouterModule],
  templateUrl: './condition.component.html',
  styleUrls: ['./condition.component.scss']
})
export class ConditionComponent {}
