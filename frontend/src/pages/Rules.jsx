import React, { useState, useEffect } from 'react';
import {
    Plus,
    MoreVertical,
    Settings2,
    TestTube,
    Clock,
    Activity,
    X
} from 'lucide-react';
import Card from '../components/ui/Card';
import Badge from '../components/ui/Badge';
import Button from '../components/ui/Button';
import classes from './Rules.module.css';

const Rules = () => {
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [activeTab, setActiveTab] = useState('all');
    const [rulesData, setRulesData] = useState([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        fetch('http://localhost:3001/api/rules')
            .then(res => res.json())
            .then(data => {
                if (data && data.rules) {
                    // Map YAML schema to UI schema
                    const mappedRules = data.rules.map((r, i) => ({
                        id: r.name || i,
                        name: r.name || 'Unnamed',
                        type: r.type || 'unknown',
                        status: 'active', // Assume active for now
                        params: {
                            ...(r.keyField ? { keyField: r.keyField } : {}),
                            ...(r.field ? { field: r.field } : {}),
                            ...(r.windowSeconds ? { windowSeconds: r.windowSeconds } : {}),
                            ...(r.windowSize ? { windowSize: r.windowSize } : {}),
                            ...(r.threshold ? { threshold: r.threshold } : {}),
                            ...(r.deviationFactor ? { deviationFactor: r.deviationFactor } : {})
                        },
                        lastTrigger: 'Unknown',
                        alerts24h: 0
                    }));
                    setRulesData(mappedRules);
                }
                setIsLoading(false);
            })
            .catch(err => {
                console.error("Failed to fetch rules:", err);
                setIsLoading(false);
            });
    }, []);

    // Filter rules based on active tab
    const filteredRules = rulesData.filter(rule =>
        activeTab === 'all' || rule.type === activeTab
    );

    return (
        <div className={`page-container ${classes.rulesContainer}`}>
            <div className="page-header flex-between">
                <div>
                    <h1 className="page-title">Detection Rules</h1>
                    <p className="page-subtitle">Configure anomaly detection rules for your event streams</p>
                </div>
                <div className="flex-center gap-3">
                    <Button variant="primary" icon={Plus} onClick={() => setIsDrawerOpen(true)}>
                        Create New Rule
                    </Button>
                </div>
            </div>

            {/* Tabs */}
            <div className={classes.tabs}>
                <button
                    className={`${classes.tab} ${activeTab === 'all' ? classes.activeTab : ''}`}
                    onClick={() => setActiveTab('all')}
                >
                    All Rules (12)
                </button>
                <button
                    className={`${classes.tab} ${activeTab === 'rate' ? classes.activeTab : ''}`}
                    onClick={() => setActiveTab('rate')}
                >
                    Rate (4)
                </button>
                <button
                    className={`${classes.tab} ${activeTab === 'threshold' ? classes.activeTab : ''}`}
                    onClick={() => setActiveTab('threshold')}
                >
                    Threshold (5)
                </button>
                <button
                    className={`${classes.tab} ${activeTab === 'statistical' ? classes.activeTab : ''}`}
                    onClick={() => setActiveTab('statistical')}
                >
                    Statistical (3)
                </button>
            </div>

            {/* Rules Grid */}
            <div className={classes.rulesGrid}>
                {isLoading ? (
                    <div className="text-secondary p-4">Loading configurations...</div>
                ) : filteredRules.length === 0 ? (
                    <div className="text-secondary p-4">No rules found.</div>
                ) : filteredRules.map(rule => (
                    <Card key={rule.id} className={classes.ruleCard}>
                        <div className={classes.ruleHeader}>
                            <div className="flex-center gap-3">
                                <Badge variant={
                                    rule.type === 'rate' ? 'accentCyan' :
                                        rule.type === 'threshold' ? 'accentPurple' : 'accentOrange'
                                }>
                                    {rule.type}
                                </Badge>
                            </div>
                            <div className={classes.statusToggle}>
                                <span className={rule.status === 'active' ? 'text-success' : 'text-tertiary'}>
                                    {rule.status === 'active' ? 'Active' : 'Paused'}
                                </span>
                                <div className={`${classes.toggle} ${rule.status === 'active' ? classes.toggleOn : classes.toggleOff}`}>
                                    <div className={classes.toggleHandle} />
                                </div>
                            </div>
                        </div>

                        <h3 className={classes.ruleTitle}>{rule.name}</h3>

                        <div className={classes.paramsList}>
                            {Object.entries(rule.params).map(([key, value]) => (
                                <div key={key} className={classes.paramItem}>
                                    <span className={classes.paramLabel}>{key}</span>
                                    <span className={classes.paramValue}>{value}</span>
                                </div>
                            ))}
                        </div>

                        <div className={classes.ruleFooter}>
                            <div className={classes.ruleMeta}>
                                <Clock size={14} />
                                <span>{rule.lastTrigger}</span>
                            </div>
                            <div className={classes.ruleMeta}>
                                <Activity size={14} />
                                <span>{rule.alerts24h} alerts/24h</span>
                            </div>
                            <button className={classes.moreBtn}>
                                <MoreVertical size={16} />
                            </button>
                        </div>
                    </Card>
                ))}
            </div>

            {/* Create Rule Drawer */}
            {isDrawerOpen && (
                <div className={classes.drawerOverlay}>
                    <div className={classes.drawer}>
                        <div className={classes.drawerHeader}>
                            <div>
                                <h2 className={classes.drawerTitle}>Create New Rule</h2>
                                <p className="text-secondary text-sm">Define a new pattern to detect</p>
                            </div>
                            <button className={classes.closeBtn} onClick={() => setIsDrawerOpen(false)}>
                                <X size={20} />
                            </button>
                        </div>

                        <div className={classes.drawerContent}>
                            <div className={classes.formGroup}>
                                <label className={classes.label}>Rule Name</label>
                                <input type="text" placeholder="e.g. high_cpu_usage" className={classes.input} />
                            </div>

                            <div className={classes.formGroup}>
                                <label className={classes.label}>Rule Type</label>
                                <select className={classes.select}>
                                    <option value="rate">Rate (Events/Time Window)</option>
                                    <option value="threshold">Threshold (Absolute Value)</option>
                                    <option value="statistical">Statistical (Moving Average)</option>
                                </select>
                            </div>

                            <div className={classes.formSection}>
                                <h4 className={classes.sectionTitle}>
                                    <Settings2 size={16} /> Parameters
                                </h4>

                                <div className={classes.formGroup}>
                                    <label className={classes.label}>Key Field (Grouping)</label>
                                    <input type="text" placeholder="e.g. userId" defaultValue="userId" className={classes.input} />
                                </div>

                                <div className="flex-between gap-4">
                                    <div className={classes.formGroup} style={{ flex: 1 }}>
                                        <label className={classes.label}>Window Size (Seconds)</label>
                                        <input type="number" defaultValue={60} className={classes.input} />
                                    </div>
                                    <div className={classes.formGroup} style={{ flex: 1 }}>
                                        <label className={classes.label}>Threshold (Count)</label>
                                        <input type="number" defaultValue={100} className={classes.input} />
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className={classes.drawerFooter}>
                            <Button variant="ghost" className="fullWidth" onClick={() => setIsDrawerOpen(false)}>Cancel</Button>
                            <Button variant="outline" icon={TestTube} className="fullWidth">Test Rule</Button>
                            <Button variant="primary" className="fullWidth">Save Rule</Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Rules;
