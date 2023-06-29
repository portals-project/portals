// Run this file to generate the expected outputs of example files if their 
// outputs do not yet exist.

const fs = require('fs');
const path = require('path');
const exampleDirectory = 'portals-js/js/src/main/resources/examples/'; // the directory containing the examples
const portalsJS = 'portals-js/js/target/scala-3.3.0/portals-js.js'; // the PortalsJS library
const portalsJSCode = fs.readFileSync(portalsJS, 'utf8');

// files for which outputs are not generated
const ignoredFiles = [
  'union.js',
  'splitter.js',
]

// helper function to capture the output to console.log
function LogCapture() {
  var log = "";
  const consoleLog = console.log;
  return {
    startCapturing() { console.log = (msg) => { log += msg + '\n' } },
    stopCapturing() { console.log = consoleLog },
    clear() { log = "" },
    retrieve() { return log },
  };
}
const log = LogCapture();

// read all example files from the example directory, if no output exists, then 
// create the corresponding output file
fs.readdirSync(exampleDirectory).forEach((file) => {
  if (file.endsWith('.js')) {
    // example
    const exampleFilePath = path.join(exampleDirectory, file);
    const example = fs.readFileSync(exampleFilePath, 'utf8');
    const exampleCode = portalsJSCode + "\n" + example;

    // ignore
    if (ignoredFiles.includes(file)) {
      return;
    }

    const expectedFilePath = path.join(exampleDirectory, `${file}.out`);
    try {
      expected = fs.readFileSync(expectedFilePath, 'utf8');
    } catch (error) {
      // creating the expected output file if it does not exist
      console.warn(`Expected output file not found. Creating ${expectedFilePath}`);
      log.startCapturing();
      eval(exampleCode);
      const output = log.retrieve();
      log.clear();
      log.stopCapturing();
      fs.writeFileSync(expectedFilePath, output, 'utf8');
      return;
    }
  }
});

console.log('All outputs generated!');