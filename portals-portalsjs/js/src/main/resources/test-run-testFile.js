const fs = require('fs');
const path = require('path');
const portalsJS = 'portals-portalsjs/js/target/scala-3.3.0/portals-portalsjs-fastopt.js'; // the PortalsJS library
const testFile = 'portals-portalsjs/js/src/main/resources/testFile.js'; // testFile location
const portalsJSCode = fs.readFileSync(portalsJS, 'utf8');
const example = fs.readFileSync(testFile, 'utf8');
const exampleCode = portalsJSCode + "\n" + example;
eval(exampleCode);
