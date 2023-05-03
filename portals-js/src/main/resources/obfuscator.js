const JavaScriptObfuscator = require('javascript-obfuscator');
const fs = require('fs');

// Read the original JavaScript file
const originalCode = fs.readFileSync('portals-js/target/scala-3.2.0/portals-js-opt.js', 'utf8');
// Obfuscate the code
const obfuscatedCode = JavaScriptObfuscator.obfuscate(originalCode).getObfuscatedCode();
// Write the obfuscated code to a new file
fs.writeFileSync('portals-js/target/scala-3.2.0/portals-js.js', obfuscatedCode, 'utf8');
