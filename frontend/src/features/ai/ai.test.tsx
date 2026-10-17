import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderApp } from '../../test/renderApp';
import { apiPath, server } from '../../test/server';
import type { RecommendationRun } from '../recommendations/api';

const run: RecommendationRun = {
  id: 'run-1', goalId: 'goal-1', goalTitle: 'Professional home office', status: 'OK', preferredTier: 'BALANCED', budget: 1200,
  bundles: [], notes: [], reasons: [], suggestions: [], minimumFeasibleCost: null,
  stats: { algorithmVersion: 'bnb-1.0', nodesExplored: 10, durationMs: 2, complete: true }, createdAt: '2026-10-17T12:00:00Z',
};

const aiOn = () =>
  http.get(apiPath('/ai/status'), () =>
    HttpResponse.json({ enabled: true, available: true, model: 'llama3.2:3b', message: 'Using llama3.2:3b on Ollama.' }));

describe('AI assistance', () => {
  it('tells shoppers whether goals are read with AI or built-in rules', async () => {
    renderApp('/goals');
    expect(await screen.findByText('Built-in rules (AI assist off)')).toBeInTheDocument();
  });

  it('shows the AI model when it is available', async () => {
    server.use(aiOn());
    renderApp('/goals');
    expect(await screen.findByText('AI assist on · llama3.2:3b')).toBeInTheDocument();
  });

  it('writes an AI overview of the bundles on request', async () => {
    server.use(
      aiOn(),
      http.get(apiPath('/recommendations/run-1'), () => HttpResponse.json(run)),
      http.post(apiPath('/recommendations/run-1/explanation'), () =>
        HttpResponse.json({ status: 'OK', text: 'The balanced bundle gives you the most comfort for the money.', model: 'llama3.2:3b', cached: false, message: null })),
    );
    const { user } = renderApp('/recommendations/run-1');

    await user.click(await screen.findByRole('button', { name: 'Explain with AI' }));

    expect(await screen.findByText('The balanced bundle gives you the most comfort for the money.')).toBeInTheDocument();
    expect(screen.getByText(/never adds prices or numbers/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Explain with AI' })).toBeNull();
  });

  it('explains when an AI answer was discarded', async () => {
    server.use(
      aiOn(),
      http.get(apiPath('/recommendations/run-1'), () => HttpResponse.json(run)),
      http.post(apiPath('/recommendations/run-1/explanation'), () =>
        HttpResponse.json({ status: 'REJECTED', text: null, model: 'llama3.2:3b', cached: false,
          message: 'The AI answer mentioned details that are not in your results, so it was discarded. Please try again.' })),
    );
    const { user } = renderApp('/recommendations/run-1');

    await user.click(await screen.findByRole('button', { name: 'Explain with AI' }));

    expect(await screen.findByText(/so it was discarded/)).toBeInTheDocument();
  });

  it('hides the AI overview when AI assistance is off', async () => {
    server.use(http.get(apiPath('/recommendations/run-1'), () => HttpResponse.json(run)));
    renderApp('/recommendations/run-1');

    expect(await screen.findByRole('heading', { name: 'Your bundles' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /AI overview/ })).toBeNull();
  });
});
