import { useEffect, useState } from 'react';
import {
  Alert, Box, Button, CircularProgress, FormControlLabel, Paper, Stack, Switch, Typography,
} from '@mui/material';
import {
  notificationPreferenceApi,
  type NotificationKind,
  type NotificationPreferenceItem,
} from '../api/notificationPreferences';
import { errorMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { useSnackbar } from '../components/SnackbarProvider';

/**
 * Kisinin KENDI bildirim tercihleri.
 *
 * Secenek listesi SUNUCUDAN geliyor; arayuzde ayrica tutulsaydi sunucuya yeni
 * bir tur eklendiginde ekranda gorunmez ve kullanicinin kapatamayacagi bir
 * bildirim olusurdu.
 */
export function NotificationPreferencesPage() {
  const { notify } = useSnackbar();

  const [items, setItems] = useState<NotificationPreferenceItem[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    notificationPreferenceApi
      .mine()
      .then((preferences) => setItems(preferences.items))
      .catch((cause) => setError(errorMessage(cause)))
      .finally(() => setLoading(false));
  }, []);

  /**
   * Anahtar cevrilir cevrilmez KAYDEDILIR.
   *
   * Ayri bir "Save" dugmesi olsaydi kullanici anahtari cevirip sayfadan
   * cikabilir ve tercihinin kaydedildigini SANIRDI -- sessizce kaybolan bir
   * ayar, hic olmayan bir ayardan kotudur.
   */
  const toggle = async (kind: NotificationKind, enabled: boolean) => {
    if (!items) {
      return;
    }

    const next = items.map((item) => (item.kind === kind ? { ...item, enabled } : item));

    // Ekran ONCE guncelleniyor: anahtarin gecikmeli tepki vermesi, tiklamanin
    // kaydedilmedigi hissini verir.
    setItems(next);
    setSaving(true);
    setError(null);

    try {
      const saved = await notificationPreferenceApi.replace(
        next.filter((item) => item.enabled).map((item) => item.kind),
      );
      setItems(saved.items);
      notify('Notification preferences saved');
    } catch (cause) {
      // Sunucu reddettiyse ekran GERI ALINIR; aksi halde kullanici kapali
      // sandigi bir bildirimi almaya devam ederdi.
      setItems(items);
      setError(errorMessage(cause));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Stack spacing={2.5} sx={{ maxWidth: 520, mx: 'auto' }}>
      <PageHeader
        eyebrow="Your account"
        title="Notifications"
        description="Choose which emails this system sends you"
      />

      <Paper sx={{ p: 3 }}>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={28} />
          </Box>
        ) : (
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}

            {items?.map((item) => (
              <FormControlLabel
                key={item.kind}
                control={
                  <Switch
                    checked={item.enabled}
                    disabled={saving}
                    onChange={(event) => toggle(item.kind, event.target.checked)}
                  />
                }
                label={item.label}
              />
            ))}

            {/* Kapatilamayan bildirimler ACIKCA yaziliyor: listede gormeyen
                kullanici onlarin da kapatilabildigini sanip aramaya calisirdi. */}
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              Emails about changes to your own employee record cannot be switched off. They tell
              you when somebody edits your details, so turning them off would hide exactly the
              change you would want to know about.
            </Typography>
          </Stack>
        )}
      </Paper>

      {!loading && !items && !error && (
        <Button href="/employees">Back to the directory</Button>
      )}
    </Stack>
  );
}
