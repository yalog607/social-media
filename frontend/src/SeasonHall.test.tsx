import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { SeasonHall, parseSeasons } from './SeasonHall';

const items = [
  { id: 'new', name: 'New <script>alert(1)</script>', hashtag: 'pilot', startDate: '07:00 02/01/2025', endDate: '07:00 03/01/2025', imageUrl: '/uploads/cover.png' },
  { id: 'old', name: 'Older', hashtag: 'old', startDate: '07:00 01/01/2025', endDate: '07:00 02/01/2025', imageUrl: null },
];

describe('season hall display contract', () => {
  it('shows the existing empty state and MVC home link', () => {
    const html = renderToStaticMarkup(<SeasonHall items={[]} />);
    expect(html).toContain('Bảng vàng đang chờ những cái tên đầu tiên');
    expect(html).toContain('href="/"');
    expect(html).not.toContain('hall-season__body');
  });
  it('preserves server order, dates, images and MVC links while escaping names', () => {
    const html = renderToStaticMarkup(<SeasonHall items={items} />);
    expect(html.indexOf('/seasons/new')).toBeLessThan(html.indexOf('/seasons/old'));
    expect(html).toContain('07:00 02/01/2025');
    expect(html).toContain('Giờ Việt Nam');
    expect(html).toContain('/uploads/cover.png');
    expect(html).toContain('aria-hidden="true">🏆');
    expect(html).toContain('&lt;script&gt;');
    expect(html).not.toContain('<script>alert');
  });
  it('accepts empty and populated bootstrap without changing user text', () => {
    expect(parseSeasons('[]')).toEqual([]);
    expect(parseSeasons(JSON.stringify(items))).toEqual(items);
  });
  it('rejects broken or incompatible bootstrap instead of showing a false empty state', () => {
    expect(() => parseSeasons('{')).toThrow();
    expect(() => parseSeasons('{}')).toThrow();
    expect(() => parseSeasons('[{"id":1}]')).toThrow();
  });
});
