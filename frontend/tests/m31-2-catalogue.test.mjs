import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { destinationAssets } from '../src/features/customer/visualAssets.ts';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { DestinationDiscovery } = await import('../src/features/marketplace/DestinationDiscovery.tsx');

test('carousel retains six original images and exact selected IDs from beyond autocomplete page', () => {
  const locations=destinationAssets.map((a,i)=>({id:8000+i,name:a.locationNames[0]}));
  const routes=locations.map((l,i)=>({id:9000+i,name:`Route ${i}`,pickupLocationId:7000,dropoffLocationId:l.id}));
  const html=renderToStaticMarkup(React.createElement(MemoryRouter,null,
    React.createElement(DestinationDiscovery,{locations,routes,pending:false,error:null,retry:()=>{}})));
  assert.equal((html.match(/class="card destination-card"/g)||[]).length,6);
  for(const [i,asset] of destinationAssets.entries()) {
    assert(html.includes(asset.imageUrl));
    assert(html.includes(`dropoffLocationId=${8000+i}`));
  }
  assert(!html.includes('chưa có tuyến được giới thiệu'));
});
