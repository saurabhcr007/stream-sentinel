import React, { useState, useEffect } from 'react';
import {
    Activity,
    AlertTriangle,
    ShieldCheck,
    Clock,
    ArrowUpRight,
    TrendingUp,
    CheckCircle2
} from 'lucide-react';
import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer
} from 'recharts';
import { io } from 'socket.io-client';
import Card from '../components/ui/Card';
import Badge from '../components/ui/Badge';
import Button from '../components/ui/Button';
import classes from './Dashboard.module.css';

// Custom Tooltip for Recharts
const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
        return (
            <div className={classes.chartTooltip}>
                <p className={classes.tooltipTime}>{label}</p>
                <div className={classes.tooltipItems}>
                    {payload.map((entry, index) => (
                        <div key={`item-${index}`} className={classes.tooltipItem}>
                            <span
                                className={classes.tooltipColorIndicator}
                                style={{ backgroundColor: entry.color }}
                            />
                            <span className={classes.tooltipName}>{entry.name}:</span>
                            <span className={classes.tooltipValue}>{entry.value} e/s</span>
                        </div>
                    ))}
                </div>
            </div>
        );
    }
    return null;
};

const Dashboard = () => {
    const [chartData, setChartData] = useState([]);
    const [recentAlerts, setRecentAlerts] = useState([]);

    useEffect(() => {
        // Connect to the API Gateway
        const socket = io('http://localhost:3001');

        // Listen for mock metrics updates
        socket.on('metrics_update', (data) => {
            setChartData(prevData => {
                const newData = [...prevData, data];
                // Keep only the last 20 data points for the moving chart
                if (newData.length > 20) {
                    newData.shift();
                }
                return newData;
            });
        });

        // Listen for real Kafka alerts
        socket.on('new_alert', (alertData) => {
            setRecentAlerts(prevAlerts => {
                // Map backend schema to UI schema
                const newAlert = {
                    id: alertData.eventId || Date.now().toString(),
                    rule: alertData.ruleName || 'Unknown',
                    severity: alertData.severity || 'warning',
                    key: alertData.key || 'N/A',
                    detail: alertData.details || 'Alert Triggered',
                    time: new Date(alertData.timestamp || Date.now()).toLocaleTimeString()
                };
                const updatedAlerts = [newAlert, ...prevAlerts];
                // Keep only the latest 10 alerts
                return updatedAlerts.slice(0, 10);
            });
        });

        return () => {
            socket.disconnect();
        };
    }, []);

    // Compute live values or default to 0 if no data
    const currentTotal = chartData.length > 0
        ? (chartData[chartData.length - 1].normal + chartData[chartData.length - 1].auth + chartData[chartData.length - 1].payments)
        : 0;

    return (
        <div className="page-container">
            <div className="page-header flex-between">
                <div>
                    <h1 className="page-title">Monitoring Dashboard</h1>
                    <p className="page-subtitle">Real-time anomaly detection metrics and system health</p>
                </div>
                <div className="flex-center gap-3">
                    <Button variant="outline" icon={Activity}>Generate Report</Button>
                    <Button variant="primary">Add Detection Rule</Button>
                </div>
            </div>

            {/* Summary Metrics Row */}
            <div className={classes.metricsGrid}>
                <Card className={classes.metricCard}>
                    <div className={classes.metricHeader}>
                        <span className={classes.metricLabel}>Current Processing Rate</span>
                        <div className={classes.metricIconWrap} style={{ color: 'var(--accent-cyan)' }}>
                            <Activity size={20} />
                        </div>
                    </div>
                    <div className={classes.metricValue}>{currentTotal} <span style={{ fontSize: '1rem', color: 'var(--text-secondary)' }}>e/s</span></div>
                    <div className={classes.metricTrend}>
                        <span className="text-secondary" style={{ fontSize: '0.875rem' }}>Live WebSocket Feed</span>
                    </div>
                </Card>

                <Card className={classes.metricCard}>
                    <div className={classes.metricHeader}>
                        <span className={classes.metricLabel}>Anomalies Detected</span>
                        <div className={classes.metricIconWrap} style={{ color: 'var(--color-critical)' }}>
                            <AlertTriangle size={20} />
                        </div>
                    </div>
                    <div className={classes.metricValue}>847</div>
                    <div className={classes.metricTrend}>
                        <span className="text-critical flex-center gap-2" style={{ fontSize: '0.875rem' }}>
                            <TrendingUp size={16} /> +3%
                        </span>
                        <span className="text-secondary" style={{ fontSize: '0.875rem' }}>vs last hour</span>
                    </div>
                </Card>

                <Card className={classes.metricCard}>
                    <div className={classes.metricHeader}>
                        <span className={classes.metricLabel}>Active Rules</span>
                        <div className={classes.metricIconWrap} style={{ color: 'var(--accent-purple)' }}>
                            <ShieldCheck size={20} />
                        </div>
                    </div>
                    <div className={classes.metricValue}>12</div>
                    <div className={classes.metricTrend}>
                        <span className="text-secondary" style={{ fontSize: '0.875rem' }}>All rules running smoothly</span>
                    </div>
                </Card>

                <Card className={classes.metricCard}>
                    <div className={classes.metricHeader}>
                        <span className={classes.metricLabel}>Avg Latency</span>
                        <div className={classes.metricIconWrap} style={{ color: 'var(--color-success)' }}>
                            <Clock size={20} />
                        </div>
                    </div>
                    <div className={classes.metricValue}>3.2ms</div>
                    <div className={classes.metricTrend}>
                        <span className="text-success flex-center gap-2" style={{ fontSize: '0.875rem' }}>
                            <CheckCircle2 size={16} /> Within SLA (5ms)
                        </span>
                    </div>
                </Card>
            </div>

            {/* Main Content Area */}
            <div className={classes.mainContentGrid}>

                {/* Real-time Chart */}
                <Card className={classes.chartSection}>
                    <div className="flex-between" style={{ marginBottom: '1.5rem' }}>
                        <div>
                            <h3 style={{ fontSize: '1.25rem', marginBottom: '0.25rem' }}>Event Processing Rate</h3>
                            <p className="text-secondary" style={{ fontSize: '0.875rem' }}>Events per second over the last hour</p>
                        </div>
                    </div>

                    <div className={classes.chartContainer}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                <XAxis
                                    dataKey="time"
                                    stroke="var(--text-tertiary)"
                                    tick={{ fill: 'var(--text-tertiary)', fontSize: 12 }}
                                    dy={10}
                                    axisLine={false}
                                    tickLine={false}
                                />
                                <YAxis
                                    stroke="var(--text-tertiary)"
                                    tick={{ fill: 'var(--text-tertiary)', fontSize: 12 }}
                                    axisLine={false}
                                    tickLine={false}
                                    tickFormatter={(value) => value >= 1000 ? `${value / 1000}k` : value}
                                />
                                <Tooltip content={<CustomTooltip />} />
                                <Line
                                    type="monotone"
                                    dataKey="normal"
                                    name="System Events"
                                    stroke="var(--accent-cyan)"
                                    strokeWidth={2}
                                    dot={false}
                                    activeDot={{ r: 6, fill: 'var(--accent-cyan)', stroke: 'var(--bg-dark)' }}
                                />
                                <Line
                                    type="monotone"
                                    dataKey="auth"
                                    name="Auth Service"
                                    stroke="var(--accent-purple)"
                                    strokeWidth={2}
                                    dot={false}
                                    activeDot={{ r: 6, fill: 'var(--accent-purple)', stroke: 'var(--bg-dark)' }}
                                />
                                <Line
                                    type="monotone"
                                    dataKey="payments"
                                    name="Payments"
                                    stroke="var(--accent-orange)"
                                    strokeWidth={2}
                                    dot={false}
                                    activeDot={{ r: 6, fill: 'var(--accent-orange)', stroke: 'var(--bg-dark)' }}
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                </Card>

                {/* Recent Alerts */}
                <Card noPadding className={classes.alertsSection}>
                    <div className={classes.alertsHeader}>
                        <h3 style={{ fontSize: '1.15rem' }}>Recent Alerts</h3>
                        <Button variant="ghost" size="sm">View All</Button>
                    </div>
                    <div className={classes.alertsList}>
                        {recentAlerts.map(alert => (
                            <div key={alert.id} className={classes.alertItem}>
                                <div className={classes.alertIconWrap}>
                                    {alert.severity === 'critical' && <div className={`${classes.statusDot} bg-critical`} style={{ background: 'var(--color-critical)' }} />}
                                    {alert.severity === 'warning' && <div className={`${classes.statusDot} bg-warning`} style={{ background: 'var(--color-warning)' }} />}
                                    {alert.severity === 'info' && <div className={`${classes.statusDot} bg-info`} style={{ background: 'var(--color-info)' }} />}
                                </div>
                                <div className={classes.alertContent}>
                                    <div className="flex-between">
                                        <span className={classes.alertRuleName}>{alert.rule}</span>
                                        <span className={classes.alertTime}>{alert.time}</span>
                                    </div>
                                    <div className={classes.alertMeta}>
                                        <span className={classes.alertKey}>{alert.key}</span>
                                        <span className={classes.alertDetail}>{alert.detail}</span>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </Card>
            </div>
        </div>
    );
};

export default Dashboard;
