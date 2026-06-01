import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

declare global {
  interface Window {
    __GLEA_CONFIG__?: {
      apiBaseUrl?: string;
    };
  }
}

type IdName = { id: string; name: string; farmId?: string };
type DeviceOption = { id: string; name: string; zoneId?: string; farmId?: string };
type TimeRange = '1h' | '6h' | '24h' | '7d';
type LoadState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

type SnapshotCard = {
  label: string;
  value: string;
  secondary: string;
  tone: 'default' | 'success' | 'warn' | 'danger';
};

type ReadingPoint = {
  ts: string;
  value: number;
};

type AlertItem = {
  id: string;
  title: string;
  severity: string;
  status: string;
  timestamp?: string;
  scope?: string;
  description?: string;
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiBaseUrl = window.__GLEA_CONFIG__?.apiBaseUrl || 'http://localhost:8080/api';
  private readonly orgCode = 'default';

  readonly farms = signal<IdName[]>([]);
  readonly zones = signal<IdName[]>([]);
  readonly devices = signal<DeviceOption[]>([]);

  readonly selectedFarmId = signal<string>('');
  readonly selectedZoneId = signal<string>('');
  readonly selectedDeviceId = signal<string>('');
  readonly selectedRange = signal<TimeRange>('24h');

  readonly state = signal<LoadState>('loading');
  readonly errorMessage = signal<string>('');
  readonly snapshotCards = signal<SnapshotCard[]>([]);
  readonly readings = signal<ReadingPoint[]>([]);
  readonly alerts = signal<AlertItem[]>([]);
  readonly updatedAt = signal<string>('');

  readonly filteredZones = computed(() => {
    const farmId = this.selectedFarmId();
    if (!farmId) return this.zones();
    return this.zones().filter((zone) => !zone.farmId || zone.farmId === farmId);
  });

  readonly filteredDevices = computed(() => {
    const farmId = this.selectedFarmId();
    const zoneId = this.selectedZoneId();
    return this.devices().filter((device) => {
      const byFarm = !farmId || !device.farmId || device.farmId === farmId;
      const byZone = !zoneId || !device.zoneId || device.zoneId === zoneId;
      return byFarm && byZone;
    });
  });

  readonly activeAlerts = computed(
    () => this.alerts().filter((alert) => this.normalizeText(alert.status) === 'active').length
  );

  readonly chartPath = computed(() => {
    const points = this.readings();
    if (!points.length) return '';

    const width = 100;
    const height = 32;
    const values = points.map((point) => point.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;

    return points
      .map((point, index) => {
        const x = points.length === 1 ? width / 2 : (index / (points.length - 1)) * width;
        const y = height - ((point.value - min) / range) * height;
        return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(' ');
  });

  readonly chartMeta = computed(() => {
    const points = this.readings();
    if (!points.length) {
      return { min: '—', max: '—', current: '—' };
    }

    const values = points.map((point) => point.value);
    return {
      min: this.formatNumber(Math.min(...values)),
      max: this.formatNumber(Math.max(...values)),
      current: this.formatNumber(values[values.length - 1])
    };
  });

  constructor() {
    this.loadFiltersAndData();
  }

  onFarmChange(farmId: string): void {
    this.selectedFarmId.set(farmId);

    if (!this.filteredZones().some((zone) => zone.id === this.selectedZoneId())) {
      this.selectedZoneId.set('');
    }
    if (!this.filteredDevices().some((device) => device.id === this.selectedDeviceId())) {
      this.selectedDeviceId.set('');
    }

    this.loadObservability();
  }

  onZoneChange(zoneId: string): void {
    this.selectedZoneId.set(zoneId);

    if (!this.filteredDevices().some((device) => device.id === this.selectedDeviceId())) {
      this.selectedDeviceId.set('');
    }

    this.loadObservability();
  }

  onDeviceChange(deviceId: string): void {
    this.selectedDeviceId.set(deviceId);
    this.loadObservability();
  }

  onRangeChange(range: TimeRange): void {
    this.selectedRange.set(range);
    this.loadObservability();
  }

  retry(): void {
    this.loadFiltersAndData();
  }

  trackById(_: number, item: IdName | DeviceOption | AlertItem): string {
    return item.id;
  }

  formatAlertSeverity(severity: string): string {
    return this.normalizeText(severity).toUpperCase() || 'INFO';
  }

  formatDate(value?: string): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;

    return new Intl.DateTimeFormat('es-ES', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  }

  private loadFiltersAndData(): void {
    this.state.set('loading');
    this.errorMessage.set('');

    forkJoin({
      farms: this.fetchFarms(),
      devices: this.fetchDevices()
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ farms, devices }) => {
          const zones = this.buildZonesFromDevices(devices);

          this.farms.set(farms);
          this.devices.set(devices);
          this.zones.set(zones);

          if (!this.selectedFarmId() && farms.length) {
            this.selectedFarmId.set(farms[0].id);
          }
          if (!this.selectedZoneId() && this.filteredZones().length) {
            this.selectedZoneId.set(this.filteredZones()[0].id);
          }
          if (!this.selectedDeviceId() && this.filteredDevices().length) {
            this.selectedDeviceId.set(this.filteredDevices()[0].id);
          }

          this.loadObservability();
        },
        error: () => {
          this.state.set('error');
          this.errorMessage.set('No se pudieron cargar los filtros de inventario.');
        }
      });
  }

  private loadObservability(): void {
    this.state.set('loading');
    this.errorMessage.set('');

    forkJoin({
      latest: this.fetchLatest(),
      readings: this.fetchReadings(),
      alerts: this.fetchAlerts()
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ latest, readings, alerts }) => {
          this.alerts.set(alerts);
          this.snapshotCards.set(this.mapSnapshotCards(latest, alerts));
          this.readings.set(readings);
          this.updatedAt.set(new Date().toISOString());

          const hasData = this.snapshotCards().length > 0 || this.readings().length > 0 || this.alerts().length > 0;
          this.state.set(hasData ? 'ready' : 'empty');
        },
        error: () => {
          this.state.set('error');
          this.errorMessage.set('No se pudo cargar la pantalla de observabilidad.');
        }
      });
  }

  private fetchFarms() {
    return this.http
      .get<any>(`${this.apiBaseUrl}/farms`, { headers: this.defaultHeaders() })
      .pipe(
        map((response) => this.extractCollection(response).map((item) => ({
          id: String(this.readAny(item, ['id']) || ''),
          name: String(this.readAny(item, ['name', 'code']) || 'Finca')
        })).filter((item) => item.id)),
        catchError(() => of([] as IdName[]))
      );
  }

  private fetchDevices() {
    return this.http
      .get<any>(`${this.apiBaseUrl}/devices`, { headers: this.defaultHeaders() })
      .pipe(
        map((response) => this.extractCollection(response).map((item) => ({
          id: String(this.readAny(item, ['id']) || ''),
          name: String(this.readAny(item, ['name', 'deviceUid']) || 'Dispositivo'),
          zoneId: this.readAny(item, ['zoneId', 'zone.id']) || undefined,
          farmId: this.readAny(item, ['farmId', 'farm.id']) || undefined
        })).filter((item) => item.id)),
        catchError(() => of([] as DeviceOption[]))
      );
  }

  private fetchLatest() {
    return this.http
      .get<any>(`${this.apiBaseUrl}/telemetry/latest`, {
        headers: this.defaultHeaders(),
        params: this.buildObservabilityParams()
      })
      .pipe(
        map((response) => Array.isArray(response) ? response[0] ?? null : response),
        catchError(() => of(null))
      );
  }

  private fetchReadings() {
    return this.http
      .get<any>(`${this.apiBaseUrl}/telemetry/readings`, {
        headers: this.defaultHeaders(),
        params: this.buildObservabilityParams()
      })
      .pipe(
        map((response) => this.extractCollection(response)
          .map((item) => ({
            ts: String(this.readAny(item, ['ts', 'timestamp', 'time']) || ''),
            value: Number(this.readAny(item, ['value', 'valueNum', 'latestValue']))
          }))
          .filter((item) => item.ts && !Number.isNaN(item.value))
          .sort((a, b) => new Date(a.ts).getTime() - new Date(b.ts).getTime())),
        catchError(() => of([] as ReadingPoint[]))
      );
  }

  private fetchAlerts() {
    return this.http
      .get<any>(`${this.apiBaseUrl}/alerts`, {
        headers: this.defaultHeaders(),
        params: this.buildObservabilityParams()
      })
      .pipe(
        map((response) => this.extractCollection(response).map((item, index) => ({
          id: String(this.readAny(item, ['id']) || `alert-${index}`),
          title: String(this.readAny(item, ['title', 'name', 'code', 'type']) || 'Alerta'),
          severity: String(this.readAny(item, ['severity', 'level']) || 'INFO'),
          status: String(this.readAny(item, ['status', 'state']) || 'ACTIVE'),
          timestamp: this.readAny(item, ['timestamp', 'createdAt', 'ts', 'alertTs']) || undefined,
          scope: this.readAny(item, ['scope', 'deviceName', 'deviceUid', 'zoneName']) || undefined,
          description: this.readAny(item, ['description', 'message']) || undefined
        }))),
        catchError(() => of([] as AlertItem[]))
      );
  }

  private defaultHeaders(): HttpHeaders {
    return new HttpHeaders({ 'X-Org-Code': this.orgCode });
  }

  private buildObservabilityParams(): HttpParams {
    let params = new HttpParams();
    const { from, to } = this.resolveDateRange(this.selectedRange());

    params = params.set('from', from.toISOString()).set('to', to.toISOString());

    if (this.selectedFarmId()) params = params.set('farmId', this.selectedFarmId());
    if (this.selectedZoneId()) params = params.set('zoneId', this.selectedZoneId());
    if (this.selectedDeviceId()) params = params.set('deviceId', this.selectedDeviceId());
    return params;
  }

  private extractCollection(response: any): any[] {
    if (Array.isArray(response)) return response;
    if (Array.isArray(response?.content)) return response.content;
    if (Array.isArray(response?.items)) return response.items;
    if (Array.isArray(response?.data)) return response.data;
    return [];
  }

  private buildZonesFromDevices(devices: DeviceOption[]): IdName[] {
    const seen = new Map<string, IdName>();

    devices.forEach((device) => {
      if (!device.zoneId) return;
      if (!seen.has(device.zoneId)) {
        seen.set(device.zoneId, {
          id: device.zoneId,
          name: device.zoneId,
          farmId: device.farmId
        });
      }
    });

    return Array.from(seen.values()).sort((a, b) => a.name.localeCompare(b.name));
  }

  private mapSnapshotCards(raw: any, alerts: AlertItem[]): SnapshotCard[] {
    const activeAlerts = alerts.filter((alert) => this.normalizeText(alert.status) === 'active').length;

    if (!raw && !alerts.length) return [];

    const status = this.readAny(raw, ['state', 'status', 'deviceState']) || 'UNKNOWN';
    const lastSeen = this.readAny(raw, ['lastSeenAt', 'lastSeen', 'ts', 'timestamp']);
    const value = this.readAny(raw, ['value', 'valueNum', 'latestValue']);
    const unit = this.readAny(raw, ['unit', 'unitSymbol', 'symbol']) || '';
    const deviceName = this.readAny(raw, ['deviceName', 'device.name', 'deviceUid']) || 'Dispositivo';

    return [
      {
        label: 'Último valor',
        value: value !== undefined && value !== null ? `${this.formatNumber(Number(value))} ${unit}`.trim() : '—',
        secondary: deviceName,
        tone: 'default'
      },
      {
        label: 'Estado',
        value: String(status),
        secondary: lastSeen ? `Último seen ${this.formatDate(String(lastSeen))}` : 'Sin last seen',
        tone: this.statusTone(String(status))
      },
      {
        label: 'Alertas activas',
        value: String(activeAlerts),
        secondary: 'Dentro del filtro actual',
        tone: activeAlerts > 0 ? 'warn' : 'success'
      }
    ];
  }

  private resolveDateRange(range: TimeRange): { from: Date; to: Date } {
    const to = new Date();
    const from = new Date(to);

    switch (range) {
      case '1h':
        from.setHours(from.getHours() - 1);
        break;
      case '6h':
        from.setHours(from.getHours() - 6);
        break;
      case '24h':
        from.setDate(from.getDate() - 1);
        break;
      case '7d':
        from.setDate(from.getDate() - 7);
        break;
    }

    return { from, to };
  }

  private readAny(source: any, paths: string[]): any {
    for (const path of paths) {
      const value = path.split('.').reduce((acc, key) => acc?.[key], source);
      if (value !== undefined && value !== null && value !== '') {
        return value;
      }
    }
    return null;
  }

  private normalizeText(value: string | null | undefined): string {
    return String(value || '').trim().toLowerCase();
  }

  private statusTone(status: string): 'default' | 'success' | 'warn' | 'danger' {
    const normalized = this.normalizeText(status);
    if (normalized === 'online' || normalized === 'ok') return 'success';
    if (normalized === 'offline' || normalized === 'critical') return 'danger';
    if (normalized === 'stale' || normalized === 'warning') return 'warn';
    return 'default';
  }

  private formatNumber(value: number): string {
    if (Number.isNaN(value)) return '—';
    return new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 }).format(value);
  }
}
