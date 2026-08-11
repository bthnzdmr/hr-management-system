import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  AppBar,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import MenuIcon from '@mui/icons-material/Menu';
import PersonAddAltOutlinedIcon from '@mui/icons-material/PersonAddAltOutlined';
import { useAuth } from '../auth/AuthContext';
import { useColorMode } from '../theme/ColorModeContext';

const DRAWER_WIDTH = 248;

interface NavItem {
  label: string;
  to: string;
  icon: typeof GroupsOutlinedIcon;
  adminOnly: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Employees', to: '/employees', icon: GroupsOutlinedIcon, adminOnly: false },
  { label: 'New employee', to: '/employees/new', icon: PersonAddAltOutlinedIcon, adminOnly: true },
];

export function Layout() {
  const { user, isAdmin, logout } = useAuth();
  const { mode, toggle } = useColorMode();
  const theme = useTheme();
  const location = useLocation();

  // Genis ekranda kalici, dar ekranda acilir kapanir cekmece.
  const isWide = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenu, setUserMenu] = useState<HTMLElement | null>(null);

  const visibleItems = NAV_ITEMS.filter((item) => !item.adminOnly || isAdmin);

  const drawerContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ gap: 1.5 }}>
        <Box
          sx={{
            width: 32,
            height: 32,
            borderRadius: 1.5,
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            display: 'grid',
            placeItems: 'center',
            fontWeight: 700,
            fontSize: 14,
          }}
        >
          HR
        </Box>
        <Typography variant="subtitle1" noWrap sx={{ fontWeight: 700 }}>
          People
        </Typography>
      </Toolbar>

      <Divider />

      <List sx={{ px: 1.5, py: 2, flexGrow: 1 }}>
        {visibleItems.map((item) => {
          const Icon = item.icon;
          // "/employees" her alt yola da uyar; bu yuzden tam esitlik aranir,
          // aksi halde "New employee" acikken ikisi birden secili gorunurdu.
          const selected = location.pathname === item.to;

          return (
            <ListItemButton
              key={item.to}
              component={NavLink}
              to={item.to}
              selected={selected}
              onClick={() => setMobileOpen(false)}
              sx={{ borderRadius: 2, mb: 0.5 }}
            >
              <ListItemIcon sx={{ minWidth: 38, color: selected ? 'primary.main' : 'inherit' }}>
                <Icon fontSize="small" />
              </ListItemIcon>
              <ListItemText
                primary={item.label}
                slotProps={{ primary: { sx: { fontSize: 14, fontWeight: selected ? 650 : 500 } } }}
              />
            </ListItemButton>
          );
        })}
      </List>

      <Divider />
      <Box sx={{ p: 2 }}>
        <Typography variant="caption" color="text.secondary">
          HR Management System
        </Typography>
      </Box>
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

          <Box sx={{ flexGrow: 1 }} />

          <Tooltip title={mode === 'light' ? 'Switch to dark theme' : 'Switch to light theme'}>
            <IconButton onClick={toggle} aria-label="Toggle theme">
              {mode === 'light' ? (
                <DarkModeOutlinedIcon fontSize="small" />
              ) : (
                <LightModeOutlinedIcon fontSize="small" />
              )}
            </IconButton>
          </Tooltip>

          {user && (
            <>
              <Chip
                label={isAdmin ? 'ADMIN' : 'USER'}
                size="small"
                color={isAdmin ? 'primary' : 'default'}
                variant={isAdmin ? 'filled' : 'outlined'}
              />
              <Chip
                label={user.email}
                onClick={(event) => setUserMenu(event.currentTarget)}
                variant="outlined"
                sx={{ maxWidth: 220 }}
              />
              <Menu
                anchorEl={userMenu}
                open={Boolean(userMenu)}
                onClose={() => setUserMenu(null)}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                transformOrigin={{ vertical: 'top', horizontal: 'right' }}
              >
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
            </>
          )}
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        <Drawer
          variant={isWide ? 'permanent' : 'temporary'}
          open={isWide || mobileOpen}
          onClose={() => setMobileOpen(false)}
          // Dar ekranda cekmeceyi DOM'da tutmak acilisi hizlandirir.
          ModalProps={{ keepMounted: true }}
          sx={{
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
              borderRight: `1px solid ${theme.palette.divider}`,
            },
          }}
        >
          {drawerContent}
        </Drawer>
      </Box>

      <Box component="main" sx={{ flexGrow: 1, width: { md: `calc(100% - ${DRAWER_WIDTH}px)` } }}>
        {/* Ust cubuk "fixed" oldugu icin icerigin altina kaymamasi adina
            yuksekligi kadar bosluk birakilir. */}
        <Toolbar />
        <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1280, mx: 'auto' }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
