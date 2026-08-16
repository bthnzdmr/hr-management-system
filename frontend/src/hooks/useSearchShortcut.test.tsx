import { describe, expect, it, vi } from 'vitest';
import { useRef } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSearchShortcut } from './useSearchShortcut';

function Harness({ onClear }: { onClear: () => void }) {
  const ref = useRef<HTMLInputElement>(null);
  useSearchShortcut(ref, onClear);

  return (
    <div>
      <input ref={ref} aria-label="Search" />
      <input aria-label="Job title" />
      <textarea aria-label="Notes" />
      <button type="button">Somewhere else</button>
    </div>
  );
}

describe('useSearchShortcut', () => {
  it('focuses the search box when the page has focus elsewhere', async () => {
    const user = userEvent.setup({ delay: null });
    render(<Harness onClear={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Somewhere else' }));
    await user.keyboard('/');

    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveFocus();
  });

  it('does not insert the slash it just consumed', async () => {
    const user = userEvent.setup({ delay: null });
    render(<Harness onClear={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Somewhere else' }));
    await user.keyboard('/');

    // preventDefault olmasaydi odaklanma sonrasi "/" kutuya yazilir ve
    // kullanici her kisayolda bir egik cizgi silmek zorunda kalirdi.
    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('');
  });

  it('keeps its hands off a slash typed into another field', async () => {
    // Klasik hata: kisayol her yerde calisir ve bir yola veya tarihe egik
    // cizgi yazan kullanicinin imleci baska kutuya sicrar.
    const user = userEvent.setup({ delay: null });
    render(<Harness onClear={vi.fn()} />);

    const other = screen.getByRole('textbox', { name: 'Job title' });
    await user.click(other);
    await user.keyboard('a/b');

    expect(other).toHaveFocus();
    expect(other).toHaveValue('a/b');
    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('');
  });

  it('leaves a slash typed into a textarea alone as well', async () => {
    const user = userEvent.setup({ delay: null });
    render(<Harness onClear={vi.fn()} />);

    const notes = screen.getByRole('textbox', { name: 'Notes' });
    await user.click(notes);
    await user.keyboard('before/after');

    expect(notes).toHaveValue('before/after');
  });

  it('does not steal a browser shortcut that uses a modifier', async () => {
    const user = userEvent.setup({ delay: null });
    render(<Harness onClear={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Somewhere else' }));
    await user.keyboard('{Control>}/{/Control}');

    expect(screen.getByRole('textbox', { name: 'Search' })).not.toHaveFocus();
  });

  it('clears only when Escape comes from the search box itself', async () => {
    const onClear = vi.fn();
    const user = userEvent.setup({ delay: null });
    render(<Harness onClear={onClear} />);

    // Sayfanin herhangi bir yerinde Escape aramayi silmemeli; acik bir
    // pencere varsa Escape once onu kapatmalidir.
    await user.click(screen.getByRole('button', { name: 'Somewhere else' }));
    await user.keyboard('{Escape}');
    expect(onClear).not.toHaveBeenCalled();

    await user.click(screen.getByRole('textbox', { name: 'Search' }));
    await user.keyboard('{Escape}');
    expect(onClear).toHaveBeenCalledTimes(1);
  });
});
