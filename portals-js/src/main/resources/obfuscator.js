const JavaScriptObfuscator = require('javascript-obfuscator');
const fs = require('fs');

// Read the original JavaScript file
const originalCode = fs.readFileSync('portals-js/target/scala-3.2.0/portals-js-fastopt/main.js', 'utf8');
// Obfuscate the code
const obfuscatedCode = JavaScriptObfuscator.obfuscate(originalCode, {
    compact: false,
    controlFlowFlattening: true,
    controlFlowFlatteningThreshold: 1,
    numbersToExpressions: true,
    simplify: true,
    stringArrayShuffle: true,
    splitStrings: true,
    stringArrayThreshold: 1
}).getObfuscatedCode();

// log the obfuscated code
// Write the obfuscated code to a new file
fs.writeFileSync('portals-js/target/scala-3.2.0/portals-js-fastopt/main.js', obfuscatedCode, 'utf8');