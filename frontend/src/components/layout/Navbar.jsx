import React from 'react';
import { NavLink, Link } from 'react-router-dom';
import { Activity, Bell, Shield, Settings, CheckCircle2, User } from 'lucide-react';
import classes from './Navbar.module.css';

const Navbar = () => {
    return (
        <header className={classes.header}>
            <div className={classes.container}>
                {/* Logo */}
                <Link to="/" className={classes.logo}>
                    <div className={classes.logoIcon}>
                        <Activity size={24} color="var(--accent-cyan)" />
                    </div>
                    <span className={classes.logoText}>Stream Sentinel</span>
                </Link>

                {/* Navigation Links */}
                <nav className={classes.nav}>
                    <NavLink
                        to="/dashboard"
                        className={({ isActive }) => `${classes.navLink} ${isActive ? classes.active : ''}`}
                    >
                        <Activity size={18} />
                        <span>Dashboard</span>
                    </NavLink>
                    <NavLink
                        to="/alerts"
                        className={({ isActive }) => `${classes.navLink} ${isActive ? classes.active : ''}`}
                    >
                        <Bell size={18} />
                        <span>Alerts</span>
                    </NavLink>
                    <NavLink
                        to="/rules"
                        className={({ isActive }) => `${classes.navLink} ${isActive ? classes.active : ''}`}
                    >
                        <Shield size={18} />
                        <span>Rules</span>
                    </NavLink>
                    <NavLink
                        to="/settings"
                        className={({ isActive }) => `${classes.navLink} ${isActive ? classes.active : ''}`}
                    >
                        <Settings size={18} />
                        <span>Settings</span>
                    </NavLink>
                </nav>

                {/* Right side controls */}
                <div className={classes.controls}>
                    <div className={classes.statusIndicator}>
                        <div className={classes.statusDot}></div>
                        <span className={classes.statusText}>Healthy</span>
                    </div>

                    <div className={classes.avatar}>
                        <User size={18} />
                    </div>
                </div>
            </div>
        </header>
    );
};

export default Navbar;
