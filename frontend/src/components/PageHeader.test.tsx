import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PageHeader } from './PageHeader';

describe('PageHeader', () => {
  it('exposes the title as the only first-level heading', () => {
    // Goz atan biri icin ustteki bolum adi ile baslik yan yana duruyor, ama
    // ekran okuyucu icin sayfanin adi YALNIZCA baslik olmali. Ikisi tek
    // dugumde birlesseydi baslik "Directory Employees" diye okunurdu.
    render(<PageHeader eyebrow="Directory" title="Employees" />);

    expect(screen.getByRole('heading', { level: 1, name: 'Employees' })).toBeInTheDocument();
  });

  it('renders the description and the actions it is handed', () => {
    render(
      <PageHeader
        title="Accounts"
        description="12 accounts that can sign in"
        actions={<button type="button">New account</button>}
      />,
    );

    expect(screen.getByText('12 accounts that can sign in')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New account' })).toBeInTheDocument();
  });

  it('leaves out the optional parts instead of reserving empty space', () => {
    const { container } = render(<PageHeader title="Change password" />);

    expect(screen.getByRole('heading', { level: 1, name: 'Change password' })).toBeInTheDocument();
    // Baslik disinda hicbir metin dugumu kalmamali: bos bir aciklama satiri
    // sayfada sebepsiz bir bosluk birakirdi.
    expect(container.textContent).toBe('Change password');
  });
});
