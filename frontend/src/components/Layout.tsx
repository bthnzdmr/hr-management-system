import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  AppBar,
  Box,
  ButtonBase,
  Divider,
  Drawer,
  IconButton,
  List,
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
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import MenuIcon from '@mui/icons-material/Menu';
import UnfoldMoreIcon from '@mui/icons-material/UnfoldMore';
import { useAuth } from '../auth/AuthContext';
import { useColorMode } from '../theme/ColorModeContext';
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from '../types/api';

const DRAWER_WIDTH = 248;

interface NavItem {
  label: string;
  to: string;
  icon: typeof GroupsOutlinedIcon;
  /** Hangi yetenek gerekiyor; yoksa herkese acik. */
  requires?: 'editEmployees' | 'manageAccounts' | 'viewDashboard';
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
    ],
  },
  {
    heading: 'Workspace',
    items: [
      { label: 'Overview', to: '/dashboard', icon: InsightsOutlinedIcon, requires: 'viewDashboard' },
      { label: 'Employees', to: '/employees', icon: GroupsOutlinedIcon },
      { label: 'Org chart', to: '/org-chart', icon: AccountTreeOutlinedIcon, requires: 'viewDashboard' },
      { label: 'Departments', to: '/departments', icon: ApartmentOutlinedIcon, requires: 'editEmployees' },
    ],
  },
];

export function Layout() {
  const { user, canEditEmployees, canManageAccounts, canViewDashboard, logout } = useAuth();
  const { mode, toggle } = useColorMode();
  const theme = useTheme();
  const location = useLocation();

  // Genis ekranda kalici, dar ekranda acilir kapanir cekmece.
  const isWide = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenu, setUserMenu] = useState<HTMLElement | null>(null);

  const allowed = {
    editEmployees: canEditEmployees,
    manageAccounts: canManageAccounts,
    viewDashboard: canViewDashboard,
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
      <Typography variant="subtitle1" noWrap sx={{ letterSpacing: '-0.02em' }}>
        People
      </Typography>
    </Stack>
  );

  const drawerContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar>{brand}</Toolbar>

      <Divider />

      <Box sx={{ flexGrow: 1, overflowY: 'auto', py: 1.5 }}>
        {visibleSections.map((section) => (
          <Box key={section.heading} sx={{ mb: 1.5 }}>
            <Typography
              variant="overline"
              color="text.secondary"
              sx={{ display: 'block', px: 2.5, fontSize: 10, mb: 0.5 }}
            >
              {section.heading}
            </Typography>

            <List disablePadding sx={{ px: 1.5 }}>
              {section.items.map((item) => {
                const Icon = item.icon;
                // "/employees" her alt yola da uyar; bu yuzden tam esitlik
                // aranir, aksi halde "New employee" acikken ikisi birden
                // secili gorunurdu.
                const selected = location.pathname === item.to;

                return (
                  <ListItemButton
                    key={item.to}
                    component={NavLink}
                    to={item.to}
                    selected={selected}
                    onClick={() => setMobileOpen(false)}
                    sx={{ mb: 0.25, py: 0.9 }}
                  >
                    <ListItemIcon
                      sx={{ minWidth: 34, color: selected ? 'primary.main' : 'text.secondary' }}
                    >
                      <Icon sx={{ fontSize: 19 }} />
                    </ListItemIcon>
                    <ListItemText
                      primary={item.label}
                      slotProps={{
                        primary: { sx: { fontSize: 14, fontWeight: selected ? 600 : 480 } },
                      }}
                    />
                  </ListItemButton>
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
        <Box sx={{ p: 1.5, borderTop: 1, borderColor: 'divider' }}>
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
                p: 1,
                borderRadius: 1.5,
                justifyContent: 'flex-start',
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
        sx={{ width: { md: `calc(100% - ${DRAWER_WIDTH}px)` }, ml: { md: `${DRAWER_WIDTH}px` } }}
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

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        <Drawer
          variant={isWide ? 'permanent' : 'temporary'}
          open={isWide || mobileOpen}
          onClose={() => setMobileOpen(false)}
          // Dar ekranda cekmeceyi DOM'da tutmak acilisi hizlandirir.
          ModalProps={{ keepMounted: true }}
          sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
        >
          {drawerContent}
        </Drawer>
      </Box>

      <Box component="main" sx={{ flexGrow: 1, width: { md: `calc(100% - ${DRAWER_WIDTH}px)` } }}>
        {/* Ust cubuk "fixed" oldugu icin icerigin altina kaymamasi adina
            yuksekligi kadar bosluk birakilir. */}
        <Toolbar />
        <Box sx={{ p: { xs: 2, md: 4 }, maxWidth: 1280, mx: 'auto' }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
