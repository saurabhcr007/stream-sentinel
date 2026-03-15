import React from 'react';
import classes from './Badge.module.css';

const Badge = ({ children, variant = 'info', className = '' }) => {
    return (
        <span className={`${classes.badge} ${classes[variant]} ${className}`}>
            {children}
        </span>
    );
};

export default Badge;
