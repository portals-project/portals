// The test runner exeutes all the examples in the examples directory and 
// compares the output to the expected output. If a file is in the 
// `ignoredOutputs` array, it will be executed, but its output will not be 
// compared to the expected output. To create the expected outputs, run the file
// `test-generate-ouputs.js` instead.

const fs = require('fs');
const path = require('path');
const exampleDirectory = 'portals-portalsjs/js/src/main/resources/examples/'; // the directory containing the examples
const portalsJS = 'portals-portalsjs/js/target/scala-3.3.0/portals-portalsjs.js'; // the PortalsJS library
const portalsJSCode = fs.readFileSync(portalsJS, 'utf8');

// files for which the outputs are ignored
const ignoredOutputs = [
  'union.js',
  'splitter.js',
  `multiDataflow.js`,
  `portalAggregation.js`,
]

// files which are ignored
const ignoredFiles = [
  `billionEvents.js`,
  `sleepingBeauty.js`,
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

// helper function to format the output to match the expected output by removing
// timestamps and other information before the "- " symbol for every line
function formatOutput(input) {
  var lines = input.split('\n');
  var formattedLines = lines.map(function (line) {
    var hyphenIndex = line.indexOf('-');
    if (hyphenIndex !== -1) {
      return line.substring(hyphenIndex + 2); // +2 to exclude the space after the hyphen
    }
    return line;
  });
  var formattedOutput = formattedLines.join('\n');
  return formattedOutput;
}

// process an example file, throws an error if example throws an error or if the
// output does not match the expected output
function processExampleFile(directory, file) {
  const filePath = path.join(directory, file);
  const expectedFilePath = filePath + '.out';

  const example = fs.readFileSync(filePath, 'utf8');
  const exampleCode = portalsJSCode + "\n" + example;

  // ignore files in the ignoredFiles array
  if (ignoredFiles.includes(file)) {
    return;
  }

  // if no output file exists, then only verify that it does not throw any 
  // errors
  if (ignoredOutputs.includes(file)) {
    log.startCapturing();
    eval(exampleCode);
    log.clear();
    log.stopCapturing();
    return;
  }

  expected = fs.readFileSync(expectedFilePath, 'utf8');

  // execute
  log.startCapturing();
  eval(exampleCode);
  const actual = formatOutput(log.retrieve());
  log.clear();
  log.stopCapturing();

  // verify output
  if (actual !== expected) {
    throw new Error(`Expected ${expected}, but got ${actual}`);
  }
}

// read all example files from the example directory, if an output exists, then
// compare the output to the expected output, otherwise just executed the file
// and check if it executes without errors
function processDirectory(directory) {
  fs.readdirSync(directory).forEach((file) => {
    const filePath = path.join(directory, file);
    const stats = fs.statSync(filePath);

    if (stats.isDirectory()) {
      // recursively process subdirectories
      processDirectory(filePath);

    } else if (stats.isFile() && file.endsWith('.js')) {
      // process example files
      processExampleFile(directory, file);
    }
  });
}

// process the example directory, recursively
processDirectory(exampleDirectory);

// if this message printed then all tests passed
console.log('All tests passed!');