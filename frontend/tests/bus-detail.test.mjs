import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { OperatorBusDetailPage } = await import('../src/pages/operator/OperatorBusesPages.tsx');
const { statusPresentation } = await import('../src/utils/status.ts');
const h = React.createElement;
test('bus detail translates all status options while preserving enum values and one active tab', () => {
  for (const status of ['AVAILABLE', 'MAINTENANCE', 'INACTIVE']) {
    const client = new QueryClient();
    client.setQueryData(['operator', 'bus-types'], [{ id: 2, name: 'Ghế ngồi', seatCount: 40, status: 'ACTIVE' }]);
    client.setQueryData(['operator', 'buses', 3], { id: 3, licensePlate: '51B-12345', status, busType: { id: 2, name: 'Ghế ngồi', seatCount: 40 } });
    const html = renderToStaticMarkup(h(QueryClientProvider, { client }, h(MemoryRouter, { initialEntries: ['/operator/buses/3'] }, h(Routes, null, h(Route, { path: '/operator/buses/:busId', element: h(OperatorBusDetailPage) })))));
    for (const value of ['AVAILABLE', 'MAINTENANCE', 'INACTIVE']) {
      assert.match(html, new RegExp(`<option value="${value}"(?: selected="")?>${statusPresentation('bus', value).label}</option>`));
    }
    assert.doesNotMatch(html, />AVAILABLE<|>MAINTENANCE<|>INACTIVE</);
    assert.equal((html.match(/aria-pressed="true"/g) || []).length, 1);
    assert.match(html, /aria-pressed="true">Tổng quan/);
  }
});
