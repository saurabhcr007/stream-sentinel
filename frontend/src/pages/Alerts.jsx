import React, { useState, useEffect } from 'react';
import {
    Search,
    Filter,
    Download,
    Flame,
    AlertTriangle,
    Info,
    X,
    Clock,
    Code
} from 'lucide-react';
import Card from '../components/ui/Card';
import Badge from '../components/ui/Badge';
import Button from '../components/ui/Button';
import classes from './Alerts.module.css';

import { io } from 'socket.io-client';

const Alerts = () => {
    const [selectedAlert, setSelectedAlert] = useState(null);
    const [alertsData, setAlertsData] = useState([]);

    useEffect(() => {
        // Connect to the API Gateway
        const socket = io('http://localhost:3001');

        // Listen for real Kafka alerts
        socket.on('new_alert', (alertData) => {
            setAlertsData(prevAlerts => {
                // Map backend schema to UI schema
                const newAlert = {
                    id: alertData.eventId || Date.now().toString() + Math.random(),
                    rule: alertData.ruleName || 'Unknown',
                    type: alertData.ruleType || 'threshold',
                    severity: alertData.severity || 'warning',
                    key: alertData.key || 'N/A',
                    value: alertData.value || 'N/A',
                    threshold: alertData.threshold || 'N/A',
                    details: alertData.details || 'Alert Triggered',
                    time: new Date(alertData.timestamp || Date.now()).toLocaleString(),
                    rawJson: JSON.stringify(alertData, null, 2)
                };
                const updatedAlerts = [newAlert, ...prevAlerts];
                // Keep only a reasonable amount in UI memory
                return updatedAlerts.slice(0, 100);
            });
        });

        return () => {
            socket.disconnect();
        };
    }, []);

    return (
        <div className={`page-container ${classes.alertsContainer}`}>
            <div className="page-header flex-between">
                <div>
                    <h1 className="page-title">Alerts History</h1>
                    <p className="page-subtitle">Review and manage anomaly detection events</p>
                </div>
                <div className="flex-center gap-3">
                    <Button variant="outline" icon={Download}>Export CSV</Button>
                </div>
            </div>

            {/* Filter Bar */}
            <Card className={classes.filterBar}>
                <div className={classes.searchBox}>
                    <Search size={18} className="text-tertiary" />
                    <input type="text" placeholder="Search by key, rule name, or details..." className={classes.searchInput} />
                </div>

                <div className={classes.filterControls}>
                    <div className={classes.filterGroup}>
                        <span className={classes.filterLabel}>Severity</span>
                        <select className={classes.filterSelect}>
                            <option>All</option>
                            <option>Critical</option>
                            <option>Warning</option>
                            <option>Info</option>
                        </select>
                    </div>

                    <div className={classes.filterGroup}>
                        <span className={classes.filterLabel}>Rule Type</span>
                        <select className={classes.filterSelect}>
                            <option>All</option>
                            <option>Rate</option>
                            <option>Threshold</option>
                            <option>Statistical</option>
                        </select>
                    </div>

                    <div className={classes.filterGroup}>
                        <span className={classes.filterLabel}>Time</span>
                        <select className={classes.filterSelect}>
                            <option>Last 24h</option>
                            <option>Last 1h</option>
                            <option>Last 7d</option>
                            <option>Custom</option>
                        </select>
                    </div>
                </div>
            </Card>

            {/* Stats Row */}
            <div className={classes.statsRow}>
                <Card className={classes.statCard}>
                    <div className={classes.statHeader}>
                        <span>Critical Alerts</span>
                        <Flame size={18} className="text-critical" />
                    </div>
                    <div className={classes.statValue}>23</div>
                </Card>

                <Card className={classes.statCard}>
                    <div className={classes.statHeader}>
                        <span>Warning Alerts</span>
                        <AlertTriangle size={18} className="text-warning" />
                    </div>
                    <div className={classes.statValue}>156</div>
                </Card>

                <Card className={classes.statCard}>
                    <div className={classes.statHeader}>
                        <span>Info Alerts</span>
                        <Info size={18} className="text-info" />
                    </div>
                    <div className={classes.statValue}>668</div>
                </Card>
            </div>

            {/* Main Grid: Table + Drawer */}
            <div className={classes.contentGrid}>

                {/* Alerts Table */}
                <Card noPadding className={`${classes.tableCard} ${selectedAlert ? classes.tableShrink : ''}`}>
                    <div className={classes.tableWrapper}>
                        <table className={classes.table}>
                            <thead>
                                <tr>
                                    <th>Severity</th>
                                    <th>Rule Name</th>
                                    <th>Type</th>
                                    <th>Key</th>
                                    <th>Value</th>
                                    <th>Threshold</th>
                                    <th>Timestamp</th>
                                </tr>
                            </thead>
                            <tbody>
                                {alertsData.map(alert => (
                                    <tr
                                        key={alert.id}
                                        className={`${classes.tableRow} ${selectedAlert?.id === alert.id ? classes.rowSelected : ''}`}
                                        onClick={() => setSelectedAlert(alert)}
                                    >
                                        <td>
                                            <Badge variant={alert.severity}>
                                                {alert.severity}
                                            </Badge>
                                        </td>
                                        <td className="font-medium text-primary">{alert.rule}</td>
                                        <td>
                                            <Badge variant={
                                                alert.type === 'rate' ? 'accentCyan' :
                                                    alert.type === 'threshold' ? 'accentPurple' : 'accentOrange'
                                            }>
                                                {alert.type}
                                            </Badge>
                                        </td>
                                        <td><code className={classes.codeInline}>{alert.key}</code></td>
                                        <td className="text-primary">{alert.value}</td>
                                        <td className="text-secondary">{alert.threshold}</td>
                                        <td className="text-tertiary" style={{ whiteSpace: 'nowrap' }}>{alert.time}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    <div className={classes.tablePagination}>
                        <span className="text-secondary text-sm">Showing 1-6 of 847 alerts</span>
                        <div className="flex-center gap-2">
                            <Button variant="ghost" size="sm" disabled>Previous</Button>
                            <Button variant="ghost" size="sm">Next</Button>
                        </div>
                    </div>
                </Card>

                {/* Selected Alert Drawer */}
                {selectedAlert && (
                    <Card className={classes.detailDrawer}>
                        <div className={classes.drawerHeader}>
                            <div className="flex-center gap-3">
                                <Badge variant={selectedAlert.severity}>{selectedAlert.severity}</Badge>
                                <h2 className={classes.drawerTitle}>{selectedAlert.rule}</h2>
                            </div>
                            <button
                                className={classes.closeBtn}
                                onClick={() => setSelectedAlert(null)}
                            >
                                <X size={20} />
                            </button>
                        </div>

                        <div className={classes.drawerContent}>
                            <div className={classes.detailGrid}>
                                <div className={classes.detailItem}>
                                    <span className={classes.detailLabel}>Rule Type</span>
                                    <span className={classes.detailValue}>{selectedAlert.type}</span>
                                </div>
                                <div className={classes.detailItem}>
                                    <span className={classes.detailLabel}>Timestamp</span>
                                    <span className={classes.detailValue}>{selectedAlert.time}</span>
                                </div>
                                <div className={classes.detailItem}>
                                    <span className={classes.detailLabel}>Detected Value</span>
                                    <span className="text-critical font-medium">{selectedAlert.value}</span>
                                </div>
                                <div className={classes.detailItem}>
                                    <span className={classes.detailLabel}>Threshold</span>
                                    <span className={classes.detailValue}>{selectedAlert.threshold}</span>
                                </div>
                            </div>

                            <div className={classes.timelineSection}>
                                <h3 className={classes.sectionTitle}>
                                    <Clock size={16} /> Timeline
                                </h3>
                                <div className={classes.timeline}>
                                    <div className={classes.timelineEvent}>
                                        <div className={classes.timelineDot} />
                                        <div className={classes.timelineTime}>{selectedAlert.time}</div>
                                        <div className={classes.timelineText}>Alert Triggered</div>
                                    </div>
                                    <div className={classes.timelineEvent}>
                                        <div className={classes.timelineDot} style={{ background: 'var(--text-tertiary)', boxShadow: 'none' }} />
                                        <div className={classes.timelineTime}>- 5 mins</div>
                                        <div className={classes.timelineText}>Escalation threshold crossed</div>
                                    </div>
                                </div>
                            </div>

                            <div className={classes.jsonSection}>
                                <h3 className={classes.sectionTitle}>
                                    <Code size={16} /> Original Event
                                </h3>
                                <pre className={classes.codeBlock}>
                                    {JSON.stringify(JSON.parse(selectedAlert.rawJson), null, 2)}
                                </pre>
                            </div>
                        </div>

                        <div className={classes.drawerFooter}>
                            <Button variant="ghost" className={classes.fullWidth}>Dismiss Alert</Button>
                            <Button variant="primary" className={classes.fullWidth}>Acknowledge</Button>
                        </div>
                    </Card>
                )}
            </div>
        </div>
    );
};

export default Alerts;
