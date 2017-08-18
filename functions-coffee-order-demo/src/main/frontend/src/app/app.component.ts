import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';

class MenuItem {
  constructor(private _name: string, private _remaining: number) {
    this._cups = [];
    for (let i = 1; i <= this._remaining; i++) {
      this._cups.push(i);
    }
  }

  get canTakeOrder(): boolean { return this._remaining > 0; }

  get name(): string { return this._name; }
  get remaining(): number { return this._remaining; }
  get cups(): number[] { return this._cups; }
  get selectCupClass(): string { return this._selectCupClass; }

  get selectedCups(): number { return this._selectedCups; }
  set selectedCups(value: number) { this._selectedCups = value; }

  checkCups(): void {
    if (!this._selectedCups) {
      this.flash(8);
    }
  }

  flash(remaining: number): void {
    if (remaining > 1) {
      this._selectCupClass = (remaining % 2 ? 'cuperror' : '');
      setTimeout(() => this.flash(remaining - 1), 150);
    }
  }

  private _selectedCups: number;
  private _cups: number[];
  private _selectCupClass: string;
}


class Order {
  constructor(private _item: MenuItem, private _http: HttpClient) {
    this._stopRefresh = false;
    if (this._item && this._item.selectedCups > 0 && this._item.name) {
      this._status = 'Placing order...';
      this._http.post('https://funccoffeemaker.azurewebsites.net/api/order', {
        coffee: this._item.name,
        amount: this._item.selectedCups
      }, {
        responseType: 'text'
      }).subscribe((id: string) => {
        if (id && id.length > 2) {
          if (id[0] == '"') {
            id = id.substring(1);
          }
          if (id[id.length - 1] == '"') {
            id = id.substring(0, id.length - 1);
          }
          this._orderId = id;
          console.log(this._orderId);
        }
        this.refresh();
      });
    } else {
      this._status = 'Invalid Order';
      this._statusClass = 'staterror';
    }
  }

  get item(): MenuItem { return this._item; }
  get status(): string { return this._status; }
  get statusClass(): string { return this._statusClass; }
  get stopRefresh(): boolean { return this._stopRefresh; }
  set stopRefresh(value: boolean) { this._stopRefresh = value; }

  refresh(): void {
    if (this._orderId) {
      this._http.get('https://funccoffeemaker.azurewebsites.net/api/orders/' + this._orderId + '/status', {
        responseType: 'text'
      }).subscribe((data: string) => {
        if (data && data.length > 2) {
          if (data[0] == '"') {
            data = data.substring(1);
          }
          if (data[data.length - 1] == '"') {
            data = data.substring(0, data.length - 1);
          }
          switch (data) {
            case 'NewOrder':
            case 'Preparing':
              this._status = 'Queueing...'
              break;
            case 'Making':
              this._status = 'Confirmed :)';
              this._statusClass = 'statgood';
              break;
            case 'Cancelled':
              this._status = 'Cancelled :(';
              this._statusClass = 'staterror';
              break;
            }
            if (!this.stopRefresh) {
              setTimeout(() => this.refresh(), 1000);
            }
        } else {
          this._status = 'Invalid Order';
          this._statusClass = 'staterror';
        }
      });
    } else {
      this._status = 'Invalid Order';
      this._statusClass = 'staterror';
    }
  }

  private _statusClass: string;
  private _stopRefresh: boolean;
  private _status: string;
  private _orderId: string;
}


@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  constructor(private _http: HttpClient) {
    this._refreshing = false;
  }

  ngOnInit(): void {
    this.toMenuPage();
  }

  get title() { return this._title; }
  get order() { return this._order; }
  get refreshing() { return this._refreshing; }
  get menuitems(): any[] { return this._menuitems; }

  toMenuPage(): void {
    this._title = 'Menu';
    if (this._order) {
      this._order.stopRefresh = true;
    }
    this.refresh();
  }

  toOrderPage(): void {
    this._title = 'My Order';
  }

  refresh(): void {
    this._menuitems = [];
    this._refreshing = true;
    this._http.get('https://funccoffeemaker.azurewebsites.net/api/menu').subscribe((data: any[]) => {
      this._refreshing = false;
      for (const item of data) {
        const menu = new MenuItem(item.name, item.remaining);
        this._menuitems.push(menu);
      }
    });
  }

  placeOrder(item: MenuItem): void {
    item.checkCups();
    if (item.selectedCups > 0) {
      this._order = new Order(item, this._http);
      this.toOrderPage();
    }
  }

  private _title: string;
  private _order: Order;
  private _refreshing: boolean;
  private _menuitems: MenuItem[] = [];
}
