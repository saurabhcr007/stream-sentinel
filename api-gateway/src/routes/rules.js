const express = require('express');
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const router = express.Router();

const RULES_FILE_PATH = process.env.RULES_CONFIG_PATH
    || path.resolve(__dirname, '../../../config/rules.yml');

// Helper to read YAML
const readRules = () => {
    try {
        const fileContents = fs.readFileSync(RULES_FILE_PATH, 'utf8');
        return yaml.load(fileContents);
    } catch (e) {
        if (e.code === 'ENOENT') {
            return { rules: [] }; // Return empty if file doesn't exist yet
        }
        throw e;
    }
};

// Helper to write YAML
const writeRules = (data) => {
    const yamlStr = yaml.dump(data, { indent: 2 });
    // Ensure directory exists
    fs.mkdirSync(path.dirname(RULES_FILE_PATH), { recursive: true });
    fs.writeFileSync(RULES_FILE_PATH, yamlStr, 'utf8');
};

/**
 * GET /api/rules
 * Returns the current detection rules configuration as JSON.
 */
router.get('/', (req, res) => {
    try {
        const data = readRules();
        res.json(data);
    } catch (err) {
        console.error('Failed to read rules API:', err);
        res.status(500).json({ error: 'Failed to read rules.yml', details: err.message });
    }
});

/**
 * POST /api/rules
 * Overwrites the detection rules configuration.
 */
router.post('/', (req, res) => {
    try {
        const { rules } = req.body;

        if (!Array.isArray(rules)) {
            return res.status(400).json({ error: 'Body must contain a "rules" array' });
        }

        const newData = { rules };
        writeRules(newData);

        res.json({ message: 'Rules updated successfully', data: newData });
    } catch (err) {
        console.error('Failed to write rules API:', err);
        res.status(500).json({ error: 'Failed to write rules.yml', details: err.message });
    }
});

module.exports = router;
