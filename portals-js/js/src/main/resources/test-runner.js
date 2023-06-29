// The test runner exeutes all the examples in the examples directory and 
// compares the output to the expected output. If a file is in the 
// `ignoredOutputs` array, it will be executed, but its output will not be 
// compared to the expected output. To create the expected outputs, run the file
// `test-generate-ouputs.js` instead.

const fs = require('fs');
const path = require('path');
const exampleDirectory = './examples/'; // the directory containing the examples
const portalsJS = '../../../target/scala-3.3.0/portals-js.js'; // the PortalsJS library
const portalsJSCode = fs.readFileSync(portalsJS, 'utf8');

// files for which the outputs are ignored
const ignoredOutputs = [
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

// read all example files from the example directory, if an output exists, then
// compare the output to the expected output, otherwise just executed the file
// and check if it executes without errors
fs.readdirSync(exampleDirectory).forEach((file) => {
  if (file.endsWith('.js')) {
    // example
    const exampleFilePath = path.join(exampleDirectory, file);
    const example = fs.readFileSync(exampleFilePath, 'utf8');
    const exampleCode = portalsJSCode + "\n" + example;

    // execute, verify no errors if no output file exists
    if (ignoredOutputs.includes(file)) {
      log.startCapturing();
      eval(exampleCode);
      log.clear();
      log.stopCapturing();
      return;
    }

    // expected output
    const expectedFilePath = path.join(exampleDirectory, `${file}.out`);
    expected = fs.readFileSync(expectedFilePath, 'utf8');

    // execute
    log.startCapturing();
    eval(exampleCode);
    const actual = log.retrieve();
    log.clear();
    log.stopCapturing();

    // verify output
    if (actual !== expected) {
      throw new Error(`Expected ${expected}, but got ${actual}`);
    }
  }
});

console.log('All tests passed!');