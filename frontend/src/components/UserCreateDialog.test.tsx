import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { UserCreateDialog } from './UserCreateDialog';

vi.mock('../api/users', () => ({
  userApi: { create: vi.fn() },
}));

vi.mock('./EmployeePicker', () => ({
  EmployeePicker: ({ value }: { value: { label: string } | null }) => (
    <div data-testid="picker">{value?.label ?? 'none'}</div>
  ),
}));

const alice = { id: 1, label: 'Alice Alpha', email: 'alice@example.com' };
const bob = { id: 2, label: 'Bob Beta', email: 'bob@example.com' };

function renderDialog(forEmployee: typeof alice, open = true) {
  return render(
    <UserCreateDialog
      open={open}
      onClose={() => {}}
      onCreated={() => {}}
      forEmployee={forEmployee}
    />,
  );
}

describe('UserCreateDialog', () => {
  beforeEach(() => vi.clearAllMocks());

  it('fills the form from the employee it was opened for', () => {
    renderDialog(alice);

    expect(screen.getByLabelText(/email/i)).toHaveValue('alice@example.com');
    expect(screen.getByTestId('picker')).toHaveTextContent('Alice Alpha');
  });

  it('does not keep the previous employee when reopened for someone else', async () => {
    // Olculen kusur: durum props'tan YALNIZCA ilk kurulusta okunuyordu ve
    // detay sayfasi baska bir kisiye gecerken AYNI bileseni kullaniyordu.
    // Baslik yeni kisiyi, kutular oncekini gosteriyordu -- hesap YANLIS
    // KISIYE aciliyordu ve sunucu bunu yakalayamaz: istek gecerlidir.
    const view = renderDialog(alice);

    view.rerender(
      <UserCreateDialog open onClose={() => {}} onCreated={() => {}} forEmployee={bob} />,
    );

    expect(await screen.findByDisplayValue('bob@example.com')).toBeInTheDocument();
    expect(screen.getByTestId('picker')).toHaveTextContent('Bob Beta');
  });
});
