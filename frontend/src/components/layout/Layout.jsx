import React from 'react';
import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import classes from './Layout.module.css';

const Layout = () => {
    return (
        <div className={classes.appContainer}>
            <Navbar />
            <main className={classes.mainContent}>
                <Outlet />
            </main>
        </div>
    );
};

export default Layout;
