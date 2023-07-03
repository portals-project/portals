const fs = require('fs');
const path = require('path');
const portalsJS = 'portals-js/js/target/scala-3.3.0/portals-js-fastopt.js'; // the PortalsJS library
const testFile = 'portals-js/js/src/main/resources/testFile.js'; // testFile location
const portalsJSCode = fs.readFileSync(portalsJS, 'utf8');
const example = fs.readFileSync(testFile, 'utf8');
const exampleCode = portalsJSCode + "\n" + example;
eval(exampleCode);
