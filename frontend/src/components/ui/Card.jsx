import React from 'react';
import classes from './Card.module.css';

const Card = ({ children, className = '', noPadding = false, ...props }) => {
    return (
        <div
            className={`${classes.card} ${noPadding ? classes.noPadding : ''} ${className}`}
            {...props}
        >
            {children}
        </div>
    );
};

export default Card;
