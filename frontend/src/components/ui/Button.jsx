import React from 'react';
import classes from './Button.module.css';

const Button = ({
    children,
    variant = 'primary',
    size = 'md',
    icon: Icon,
    className = '',
    onClick,
    ...props
}) => {
    return (
        <button
            className={`${classes.button} ${classes[variant]} ${classes[size]} ${className}`}
            onClick={onClick}
            {...props}
        >
            {Icon && <Icon size={size === 'sm' ? 14 : 18} />}
            {children}
        </button>
    );
};

export default Button;
