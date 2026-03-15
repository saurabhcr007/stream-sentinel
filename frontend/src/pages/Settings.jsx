import React, { useState } from 'react';
import {
    Database,
    Activity,
    BellRing,
    Key,
    Users,
    CheckCircle2,
    Save,
    RotateCcw
} from 'lucide-react';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import classes from './Settings.module.css';

const Settings = () => {
    const [activeMenu, setActiveMenu] = useState('kafka');
    const [testStatus, setTestStatus] = useState(null);

    const handleTestConnection = () => {
        setTestStatus('testing');
        setTimeout(() => setTestStatus('success'), 1500);
    };

    return (
        <div className={`page-container ${classes.settingsContainer}`}>
            <div className="page-header">
                <div>
                    <h1 className="page-title">Settings</h1>
                    <p className="page-subtitle">Manage your stream processing engine configurations</p>
                </div>
            </div>

            <div className={classes.contentGrid}>

                {/* Sidebar Navigation */}
                <div className={classes.sidebar}>
                    <button
                        className={`${classes.menuItem} ${activeMenu === 'kafka' ? classes.menuActive : ''}`}
                        onClick={() => setActiveMenu('kafka')}
                    >
                        <Database size={18} />
                        <span>Kafka Configuration</span>
                    </button>

                    <button
                        className={`${classes.menuItem} ${activeMenu === 'flink' ? classes.menuActive : ''}`}
                        onClick={() => setActiveMenu('flink')}
                    >
                        <Activity size={18} />
                        <span>Flink Configuration</span>
                    </button>

                    <button
                        className={`${classes.menuItem} ${activeMenu === 'notifications' ? classes.menuActive : ''}`}
                        onClick={() => setActiveMenu('notifications')}
                    >
                        <BellRing size={18} />
                        <span>Notifications</span>
                    </button>

                    <button
                        className={`${classes.menuItem} ${activeMenu === 'api' ? classes.menuActive : ''}`}
                        onClick={() => setActiveMenu('api')}
                    >
                        <Key size={18} />
                        <span>API Keys</span>
                    </button>

                    <button
                        className={`${classes.menuItem} ${activeMenu === 'team' ? classes.menuActive : ''}`}
                        onClick={() => setActiveMenu('team')}
                    >
                        <Users size={18} />
                        <span>Team Management</span>
                    </button>
                </div>

                {/* Main Content Area */}
                <div className={classes.mainContent}>

                    {activeMenu === 'kafka' && (
                        <div className={classes.section}>
                            <h2 className={classes.sectionTitle}>Kafka Connection</h2>
                            <p className={classes.sectionSubtitle}>Configure connection to Kafka brokers and topics.</p>

                            <Card className={classes.settingsCard}>
                                <div className={classes.formGroup}>
                                    <label className={classes.label}>Bootstrap Servers</label>
                                    <input type="text" defaultValue="localhost:9092" className={classes.input} />
                                    <span className={classes.helpText}>Comma-separated list of host:port pairs</span>
                                </div>

                                <div className={classes.formRow}>
                                    <div className={classes.formGroup}>
                                        <label className={classes.label}>Input Topic (Events)</label>
                                        <input type="text" defaultValue="events" className={classes.input} />
                                    </div>
                                    <div className={classes.formGroup}>
                                        <label className={classes.label}>Alert Topic (Output)</label>
                                        <input type="text" defaultValue="alerts" className={classes.input} />
                                    </div>
                                </div>

                                <div className={classes.formGroup}>
                                    <label className={classes.label}>Consumer Group ID</label>
                                    <input type="text" defaultValue="stream-sentinel" className={classes.input} />
                                </div>

                                <div className={classes.testAction}>
                                    <Button
                                        variant={testStatus === 'success' ? 'ghost' : 'outline'}
                                        onClick={handleTestConnection}
                                        disabled={testStatus === 'testing'}
                                    >
                                        {testStatus === 'testing' ? 'Testing...' : 'Test Connection'}
                                    </Button>

                                    {testStatus === 'success' && (
                                        <span className="text-success flex-center gap-2">
                                            <CheckCircle2 size={18} /> Connected
                                        </span>
                                    )}
                                </div>
                            </Card>
                        </div>
                    )}

                    {activeMenu === 'flink' && (
                        <div className={classes.section}>
                            <h2 className={classes.sectionTitle}>Flink Configuration</h2>
                            <p className={classes.sectionSubtitle}>Adjust execution environment settings for the streaming job.</p>

                            <Card className={classes.settingsCard}>
                                <div className={classes.formRow}>
                                    <div className={classes.formGroup}>
                                        <div className="flex-between">
                                            <label className={classes.label}>Parallelism</label>
                                            <span className="text-cyan font-medium">1</span>
                                        </div>
                                        <input type="range" min="1" max="16" defaultValue="1" className={classes.range} />
                                    </div>

                                    <div className={classes.formGroup}>
                                        <label className={classes.label}>Checkpoint Interval (ms)</label>
                                        <input type="number" defaultValue="60000" className={classes.input} />
                                    </div>
                                </div>

                                <div className={classes.formRow}>
                                    <div className={classes.formGroup}>
                                        <label className={classes.label}>Default Key Field</label>
                                        <input type="text" defaultValue="userId" className={classes.input} />
                                    </div>

                                    <div className={classes.formGroup}>
                                        <label className={classes.label}>Health Port</label>
                                        <input type="number" defaultValue="8080" className={classes.input} />
                                    </div>
                                </div>
                            </Card>
                        </div>
                    )}

                    {activeMenu === 'notifications' && (
                        <div className={classes.section}>
                            <h2 className={classes.sectionTitle}>Notification Channels</h2>
                            <p className={classes.sectionSubtitle}>Configure where anomaly alerts are routed.</p>

                            <Card className={classes.settingsCard}>
                                <div className={classes.channelHeader}>
                                    <div className="flex-center gap-3">
                                        <img src="https://cdn.worldvectorlogo.com/logos/slack-new-logo.svg" alt="Slack" className={classes.channelIcon} />
                                        <div>
                                            <h4 className={classes.channelName}>Slack Webhook</h4>
                                            <p className={classes.channelDesc}>Send alerts to a Slack channel</p>
                                        </div>
                                    </div>
                                    <div className={classes.toggleOn}>
                                        <div className={`${classes.toggleHandle} ${classes.toggleHandleOn}`} />
                                    </div>
                                </div>
                                <div className={classes.formGroup}>
                                    <div className={classes.inputAction}>
                                        <input type="password" defaultValue="https://hooks.slack.com/services/YOUR_WEBHOOK_URL_HERE" className={classes.input} />
                                        <Button variant="outline">Test</Button>
                                    </div>
                                </div>
                            </Card>

                            <Card className={classes.settingsCard}>
                                <div className={classes.channelHeader}>
                                    <div className="flex-center gap-3">
                                        <div className={classes.channelIconPlaceholder}>PD</div>
                                        <div>
                                            <h4 className={classes.channelName}>PagerDuty</h4>
                                            <p className={classes.channelDesc}>Trigger incidents for high-severity alerts</p>
                                        </div>
                                    </div>
                                    <div className={classes.toggleOff}>
                                        <div className={classes.toggleHandle} />
                                    </div>
                                </div>
                                <div className={classes.formGroup}>
                                    <input type="password" placeholder="Enter Integration Key" className={classes.input} disabled />
                                </div>
                            </Card>
                        </div>
                    )}

                    {/* Fallback for empty menus */}
                    {['api', 'team'].includes(activeMenu) && (
                        <div className={classes.emptyState}>
                            <h2 className={classes.emptyTitle}>Coming Soon</h2>
                            <p className={classes.emptyDesc}>This configuration section is currently under development.</p>
                        </div>
                    )}
                </div>
            </div>

            {/* Footer Action Bar */}
            <div className={classes.footerBar}>
                <div className={classes.footerContainer}>
                    <span className={classes.saveMeta}>Last saved: 2 minutes ago</span>
                    <div className="flex-center gap-3">
                        <Button variant="ghost" icon={RotateCcw}>Reset Defaults</Button>
                        <Button variant="primary" icon={Save}>Save Changes</Button>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Settings;
