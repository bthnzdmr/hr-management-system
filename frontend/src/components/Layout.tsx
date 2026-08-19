import { Suspense, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  AppBar,
  Box,
  ButtonBase,
  Divider,
  Drawer,
  IconButton,
  List,
  LinearProgress,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import ApartmentOutlinedIcon from '@mui/icons-material/ApartmentOutlined';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import EventBusyOutlinedIcon from '@mui/icons-material/EventBusyOutlined';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import SupervisorAccountOutlinedIcon from '@mui/icons-material/SupervisorAccountOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import MenuIcon from '@mui/icons-material/Menu';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import UnfoldMoreIcon from '@mui/icons-material/UnfoldMore';
import { ErrorBoundary } from './ErrorBoundary';
import { useAuth } from '../auth/AuthContext';
import { useColorMode } from '../theme/ColorModeContext';
import { useNavCollapsed } from '../hooks/useNavCollapsed';
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from '../types/api';

const DRAWER_WIDTH = 248;

/** Daraltilmis genislik: ikon ve dokunma hedefi sigar, metin sigmaz. */
const RAIL_WIDTH = 68;

interface NavItem {
  label: string;
  to: string;
  icon: typeof GroupsOutlinedIcon;
  /** Hangi yetenek gerekiyor; yoksa herkese acik. */
  requires?: 'editEmployees' | 'manageAccounts' | 'viewDashboard' | 'seeLeave' | 'manageTeam';
}

/**
 * Menu bolumleri.
 *
 * Duz bir liste uc ogeyle idare eder ama dorduncu eklendiginde neyin nereye
 * ait oldugu kaybolur. Bolum basliklari yapiyi ONCEDEN kurar: "Workspace"
 * gunluk is, "Administration" sistemin kendisi. Ayrim rollerle de ortusuyor.
 */
const NAV_SECTIONS: { heading: string; items: NavItem[] }[] = [
  {
    heading: 'Administration',
    items: [
      { label: 'Accounts', to: '/users', icon: ManageAccountsOutlinedIcon, requires: 'manageAccounts' },
      { label: 'Activity', to: '/activity', icon: HistoryOutlinedIcon, requires: 'manageAccounts' },
    ],
  },
  {
    heading: 'Workspace',
    items: [
      { label: 'Overview', to: '/dashboard', icon: InsightsOutlinedIcon, requires: 'viewDashboard' },
      // Yoneticinin giris ekrani; genel listenin USTUNDE cunku gunluk isi o.
      { label: 'My team', to: '/my-team', icon: SupervisorAccountOutlinedIcon, requires: 'manageTeam' },
      { label: 'Employees', to: '/employees', icon: GroupsOutlinedIcon },
      { label: 'Leave', to: '/leave', icon: EventBusyOutlinedIcon, requires: 'seeLeave' },
      { label: 'Structure', to: '/org-chart', icon: AccountTreeOutlinedIcon, requires: 'viewDashboard' },
      { label: 'Departments', to: '/departments', icon: ApartmentOutlinedIcon, requires: 'editEmployees' },
    ],
  },
];

/**
 * Icerigin ust genislik siniri.
 *
 * Onceden 1280'di. Genislik bir okuma konforu sorusudur -- satir uzadikca goz,
 * satir sonundan bir sonrakinin basina donerken kayboluyor -- ama 1280, 1920'lik
 * bir ekranda iki yana genis bosluklar birakiyordu ve yedi sutunlu tablolar
 * bosuna sikisiyordu. 1600 yogun uygulama arayuzlerinde yerlesik bir tavan:
 * tablolar nefes aliyor, satirlar hala okunabilir kaliyor.
 *
 * Formlar bu sinira hic degmiyor; onlarin kendi dar sinirlari var (520/860) ve
 * orasi dogru.
 */
const CONTENT_MAX = 1600;

/**
 * Sinirin DISINDA kalan sayfalar.
 *
 * Olcut sayfa degil ICERIGIN CINSI: organizasyon haritasi bir tuvaldir ve orada
 * fazladan her piksel dogrudan okunabilirlige donusur -- daire buyur, isim
 * sigar. Tabloda ve metinde tam tersi calisir.
 *
 * Yol adi burada bir kez daha geciyor. Rotanin kendi verisi olarak tasimak
 * (`handle` + `useMatches`) daha temiz olurdu ama veri router'ina gecmeyi
 * gerektiriyor; NAV_SECTIONS zaten ayni adlari tasidigi icin mevcut kalibin
 * disina cikilmadi.
 */
const FULL_BLEED = new Set<string>(['/org-chart']);

export function Layout() {
  const { user, canEditEmployees, canManageAccounts, canViewDashboard,
    canSeeLeave, canManageTeam, logout } = useAuth();
  const { mode, toggle } = useColorMode();
  const theme = useTheme();
  const location = useLocation();

  // Genis ekranda kalici, dar ekranda acilir kapanir cekmece.
  const isWide = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenu, setUserMenu] = useState<HTMLElement | null>(null);
  const { collapsed, toggle: toggleNav } = useNavCollapsed();

  // Daraltma yalnizca GENIS ekranda anlamli: dar ekranda cekmece zaten ustte
  // acilip kapaniyor ve orada kalici bir ray birakmak yer calardi.
  const mini = isWide && collapsed;
  const navWidth = mini ? RAIL_WIDTH : DRAWER_WIDTH;

  const allowed = {
    editEmployees: canEditEmployees,
    manageAccounts: canManageAccounts,
    viewDashboard: canViewDashboard,
    seeLeave: canSeeLeave,
    manageTeam: canManageTeam,
  };

  // Bos bolum basligi gosterilmez: yalnizca personel gorebilen birine
  // "Administration" yazip altini bos birakmak kirik gorunurdu.
  const visibleSections = NAV_SECTIONS
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => !item.requires || allowed[item.requires]),
    }))
    .filter((section) => section.items.length > 0);

  const brand = (
    <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
      <Box
        sx={{
          width: 30,
          height: 30,
          borderRadius: 1.5,
          display: 'grid',
          placeItems: 'center',
          fontSize: 13,
          fontWeight: 600,
          letterSpacing: '-0.02em',
          // Gradyan KALDIRILDI. Iki renk arasinda gecen bir marka isareti
          // dikkat cekmeye calisir; duz bir blok kendinden emin durur.
          color: 'primary.contrastText',
          bgcolor: 'primary.main',
        }}
      >
        HR
      </Box>
      {!mini && (
        <Typography variant="subtitle1" noWrap sx={{ letterSpacing: '-0.02em' }}>
          People
        </Typography>
      )}
    </Stack>
  );

  const drawerContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ justifyContent: mini ? 'center' : 'flex-start', px: mini ? 1 : undefined }}>
        {brand}
      </Toolbar>

      <Divider />

      <Box sx={{ flexGrow: 1, overflowY: 'auto', overflowX: 'hidden', py: 1.5 }}>
        {visibleSections.map((section, index) => (
          <Box key={section.heading} sx={{ mb: 1.5 }}>
            {/* Daraltilmisken baslik sigmaz, ama bolum ayrimi kaybolmamali:
                yerini bir CIZGI aliyor. Ilkinin ustune cizgi konmaz -- orada
                zaten markanin altindaki ayrac duruyor. */}
            {mini ? (
              index > 0 && <Divider sx={{ mx: 1.5, mb: 1.5 }} />
            ) : (
              <Typography
                variant="overline"
                color="text.secondary"
                sx={{ display: 'block', px: 2.5, fontSize: 10, mb: 0.5 }}
              >
                {section.heading}
              </Typography>
            )}

            <List disablePadding sx={{ px: mini ? 0.75 : 1.5 }}>
              {section.items.map((item) => {
                const Icon = item.icon;
                // "/employees" her alt yola da uyar; bu yuzden tam esitlik
                // aranir, aksi halde "New employee" acikken ikisi birden
                // secili gorunurdu.
                const selected = location.pathname === item.to;

                return (
                  // Bos baslikli ipucu hic cizilmez, yani genis menude bir
                  // sey degismiyor.
                  <Tooltip key={item.to} title={mini ? item.label : ''} placement="right">
                    <ListItemButton
                      component={NavLink}
                      to={item.to}
                      selected={selected}
                      onClick={() => setMobileOpen(false)}
                      // Daraltilmisken metin DOM'dan cikiyor ve bir ikonun tek
                      // basina erisilebilir adi yoktur: ad acikca verilmezse
                      // ekran okuyucu yalnizca "link" der.
                      aria-label={mini ? item.label : undefined}
                      sx={{
                        mb: 0.25,
                        py: 0.9,
                        justifyContent: mini ? 'center' : 'flex-start',
                        px: mini ? 0 : undefined,
                      }}
                    >
                      <ListItemIcon
                        sx={{
                          minWidth: mini ? 0 : 34,
                          color: selected ? 'primary.main' : 'text.secondary',
                        }}
                      >
                        <Icon sx={{ fontSize: 19 }} />
                      </ListItemIcon>
                      {!mini && (
                        <ListItemText
                          primary={item.label}
                          slotProps={{
                            primary: { sx: { fontSize: 14, fontWeight: selected ? 600 : 480 } },
                          }}
                        />
                      )}
                    </ListItemButton>
                  </Tooltip>
                );
              })}
            </List>
          </Box>
        ))}
      </Box>

      {/* Kimlik menunun ALTINDA duruyor.
          Onceden ust cubuktaydi: rol rozetleri ve e-posta cipi yan yana
          dizilip her sayfada gorsel gurultu yapiyordu. Kimlik her zaman
          gorunmesi gereken ama nadiren dokunulan bir bilgidir; kenar
          cubugunun dibi tam olarak bunun yeridir. */}
      {user && (
        <Box sx={{ p: mini ? 0.75 : 1.5, borderTop: 1, borderColor: 'divider' }}>
          <Tooltip
            // Tek ipucu, blogun TAMAMINDA. Onceden her rolun ustunde ayri bir
            // ipucu vardi ve tiklanabilir bir satirin icinde "yardim" imleci
            // gosteriyordu -- satir hem tiklanir hem aciklanir gorunuyordu.
            title={(
              <Box>
                <Box sx={{ fontWeight: 600, mb: 0.5 }}>{user.email}</Box>
                {user.roles.map((role) => (
                  <Box key={role} sx={{ mt: 0.25 }}>
                    {ROLE_LABELS[role]} — {ROLE_DESCRIPTIONS[role]}
                  </Box>
                ))}
              </Box>
            )}
            placement="right"
          >
            <ButtonBase
              onClick={(event) => setUserMenu(event.currentTarget)}
              // Acik bir ad verilmeseydi dugmenin erisilebilir adi icindeki
              // butun metinlerin birlesimi olurdu: "A ada@example.com HR
              // specialist System administrator".
              aria-label={`Account menu for ${user.email}`}
              aria-haspopup="menu"
              aria-expanded={Boolean(userMenu)}
              sx={{
                width: '100%',
                gap: 1.25,
                p: mini ? 0.75 : 1,
                borderRadius: 1.5,
                justifyContent: mini ? 'center' : 'flex-start',
                textAlign: 'left',
                // Cerceve blogu bir DENETIM haline getirir. Cerceve olmadan
                // kenar cubugunun dibinde duran bir metin yigini gibiydi ve
                // tiklanabildigi anlasilmiyordu.
                border: 1,
                borderColor: 'divider',
                transition: 'background-color 140ms ease, border-color 140ms ease',
                '&:hover': { bgcolor: 'action.hover', borderColor: 'text.secondary' },
              }}
            >
              <Box
                sx={{
                  width: 30,
                  height: 30,
                  flexShrink: 0,
                  // Kare: marka isaretiyle ayni dil. Daire olsaydi ayni
                  // ekranda iki farkli bicim dili olurdu.
                  borderRadius: 1.5,
                  display: 'grid',
                  placeItems: 'center',
                  fontSize: 13,
                  fontWeight: 600,
                  bgcolor: 'action.selected',
                  color: 'text.primary',
                }}
              >
                {user.email.charAt(0).toUpperCase()}
              </Box>

              {/* Daraltilmisken geriye yalnizca avatar kalir. Kimlik kaybolmaz:
                  ipucunda ve acilan menude duruyor. */}
              {!mini && (
                <>
                  <Box sx={{ minWidth: 0, flexGrow: 1 }}>
                    <Typography noWrap sx={{ fontSize: 13, fontWeight: 560, lineHeight: 1.4 }}>
                      {user.email}
                    </Typography>

                    {/* Roller TEK satirda ve ayracli. Her rol yine ayri bir dugum:
                        tek metinde birlestirilseydi "HR specialist" diye bir metin
                        DOM'da hic bulunmaz, ekran okuyucu da iki rolu tek isim
                        olarak okurdu. Ayraclar aria-hidden. */}
                    <Typography
                      component="div"
                      noWrap
                      color="text.secondary"
                      sx={{ fontSize: 11, lineHeight: 1.5 }}
                    >
                      {user.roles.map((role, index) => (
                        <Box key={role} component="span">
                          {index > 0 && (
                            <Box component="span" aria-hidden sx={{ mx: 0.5, opacity: 0.5 }}>
                              ·
                            </Box>
                          )}
                          <Box component="span">{ROLE_LABELS[role]}</Box>
                        </Box>
                      ))}
                    </Typography>
                  </Box>

                  <UnfoldMoreIcon sx={{ fontSize: 16, flexShrink: 0, color: 'text.secondary' }} />
                </>
              )}
            </ButtonBase>
          </Tooltip>
        </Box>
      )}
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar
        position="fixed"
        sx={{
          width: { md: `calc(100% - ${navWidth}px)` },
          ml: { md: `${navWidth}px` },
          transition: (t) => t.transitions.create(['width', 'margin-left'], {
            duration: t.transitions.duration.shorter,
          }),
        }}
      >
        <Toolbar sx={{ gap: 1 }}>
          <IconButton
            edge="start"
            onClick={() => setMobileOpen(true)}
            sx={{ display: { md: 'none' } }}
            aria-label="Open navigation"
          >
            <MenuIcon />
          </IconButton>


          {/* Marka yalnizca DAR ekranda ust cubukta: genis ekranda zaten
              kenar cubugunun tepesinde duruyor ve iki kez gosterilmesi
              gereksiz olurdu. */}
          <Box sx={{ display: { xs: 'flex', md: 'none' } }}>{brand}</Box>

          <Box sx={{ flexGrow: 1 }} />

          <Tooltip title={mode === 'light' ? 'Switch to dark theme' : 'Switch to light theme'}>
            <IconButton onClick={toggle} aria-label="Toggle theme" size="small">
              {mode === 'light' ? (
                <DarkModeOutlinedIcon fontSize="small" />
              ) : (
                <LightModeOutlinedIcon fontSize="small" />
              )}
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Menu
        anchorEl={userMenu}
        open={Boolean(userMenu)}
        onClose={() => setUserMenu(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        slotProps={{ paper: { sx: { width: DRAWER_WIDTH - 24, mb: 0.5 } } }}
      >
        <MenuItem component={NavLink} to="/account/password" onClick={() => setUserMenu(null)}>
          <ListItemIcon>
            <LockOutlinedIcon fontSize="small" />
          </ListItemIcon>
          Change password
        </MenuItem>
        <MenuItem
          onClick={() => {
            setUserMenu(null);
            logout();
          }}
        >
          <ListItemIcon>
            <LogoutOutlinedIcon fontSize="small" />
          </ListItemIcon>
          Sign out
        </MenuItem>
      </Menu>

      <Box component="nav" sx={{ width: { md: navWidth }, flexShrink: { md: 0 } }}>
        <Drawer
          variant={isWide ? 'permanent' : 'temporary'}
          open={isWide || mobileOpen}
          onClose={() => setMobileOpen(false)}
          // Dar ekranda cekmeceyi DOM'da tutmak acilisi hizlandirir.
          ModalProps={{ keepMounted: true }}
          sx={{
            '& .MuiDrawer-paper': {
              // Dar ekranda cekmece DAIMA tam genislik: ray, yerin kit oldugu
              // genis ekranin cozumu degil, oradaki tercihtir.
              width: isWide ? navWidth : DRAWER_WIDTH,
              boxSizing: 'border-box',
              // Daralirken metin tasar; kirpilmasi gerekir yoksa gecis
              // sirasinda yatay bir kaydirma cubugu belirir.
              overflowX: 'hidden',
              transition: (t) => t.transitions.create('width', {
                duration: t.transitions.duration.shorter,
              }),
            },
          }}
        >
          {drawerContent}
        </Drawer>

        {/* Daraltma tutamagi cubugun KENARINDA.
            Ust cubugun sol basindayken belirsizdi: dar ekranda tam o konumda
            cekmeceyi ACAN dugme duruyor, yani ayni yer iki farkli anlam
            tasiyordu. Kenardaki bir tutamak hangi paneli ittigini konumuyla
            soyler ve baska bir sey anlatmaz.

            `fixed`: kalici cekmecenin kagidi da fixed, ustelik kagida
            `overflowX: hidden` verildi -- iceri konsaydi disari tasan yarisi
            kirpilirdi. */}
        <Tooltip title={collapsed ? 'Expand navigation' : 'Collapse navigation'} placement="right">
          <IconButton
            onClick={toggleNav}
            aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'}
            aria-expanded={!collapsed}
            sx={{
              display: { xs: 'none', md: 'inline-flex' },
              position: 'fixed',
              left: `${navWidth - 13}px`,
              top: 78,
              zIndex: (t) => t.zIndex.drawer + 1,
              width: 26,
              height: 26,
              p: 0,
              color: 'text.secondary',
              bgcolor: 'background.paper',
              border: 1,
              borderColor: 'divider',
              // Konum gecisi cekmecenin genislik gecisiyle AYNI sure: ikisi
              // farkli olsaydi tutamak kagittan kopuk kayardi.
              transition: (t) => t.transitions.create(
                ['left', 'background-color', 'border-color', 'color'],
                { duration: t.transitions.duration.shorter },
              ),
              '&:hover': {
                bgcolor: 'background.paper',
                borderColor: 'text.secondary',
                color: 'text.primary',
              },
            }}
          >
            {collapsed
              ? <ChevronRightIcon sx={{ fontSize: 17 }} />
              : <ChevronLeftIcon sx={{ fontSize: 17 }} />}
          </IconButton>
        </Tooltip>
      </Box>

      {/* `minWidth: 0` sart.

          Esnek bir ogenin varsayilan `min-width` degeri `auto`'dur ve bu,
          ogenin ICERIGINDEN daha dar olmayi REDDETMESI demektir. Geni bir
          tablo veya cizim, kabugu iterek butun sayfayi tasirir ve ekranin
          altinda yatay bir kaydirma cubugu belirir.

          Daha sinsi olani: tablolar zaten `TableContainer` icinde ve
          `overflow-x: auto` tasiyor -- ama o kural ancak kapsayici
          DARALABILIYORSA calisir. Zincirdeki tek bir `min-width: auto`,
          alttaki butun kaydirma kaplarini islevsiz birakir. */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          width: { md: `calc(100% - ${navWidth}px)` },
          transition: (t) => t.transitions.create('width', {
            duration: t.transitions.duration.shorter,
          }),
        }}
      >
        {/* Ust cubuk "fixed" oldugu icin icerigin altina kaymamasi adina
            yuksekligi kadar bosluk birakilir. */}
        <Toolbar />
        <Box
          sx={{
            p: { xs: 2, md: 4 },
            maxWidth: FULL_BLEED.has(location.pathname) ? 'none' : CONTENT_MAX,
            mx: 'auto',
            minWidth: 0,
          }}
        >
          {/* Kokteki sinir kabugu da goturuyor: tek bir sayfadaki render
              hatasi menuyu ve cikis dugmesini de siliyor, tek cikis sayfayi
              yenilemek oluyordu. Buradaki ikinci sinir hatayi SAYFAYA hapseder.

              key={location.pathname} ayni zamanda "hic sifirlanmiyor" yarisini
              cozer: baska bir sayfaya gecmek siniri yeniden kurar. */}
          {/* Suspense sinirin ICINDE ve kabugun ICINDE: sayfalar tembel
              yuklendigi icin gecis sirasinda bir bekleme ani var ve o an
              menu ile cikis dugmesi EKRANDA KALMALI. Disariya konsaydi her
              sayfa gecisinde butun kabuk yanip sonerdi.

              Yedek icerik bir cark degil ince bir cizgi: 100 ms surecek bir
              is icin donen bir cark, yavasligi gizlemek yerine gorunur kilar. */}
          <ErrorBoundary key={location.pathname}>
            <Suspense fallback={<LinearProgress aria-label="Loading the page" />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </Box>
      </Box>
    </Box>
  );
}
