// Run this file to generate the expected outputs of example files if their 
// outputs do not yet exist.

const fs = require('fs');
const path = require('path');
const exampleDirectory = 'portals-portalsjs/js/src/main/resources/examples/'; // the directory containing the examples
const portalsJS = 'portals-portalsjs/js/target/scala-3.3.0/portals-portalsjs.js'; // the PortalsJS library
const portalsJSCode = fs.readFileSync(portalsJS, 'utf8');

// files for which outputs are not generated
const ignoredFiles = [
  'union.js',
  'splitter.js',
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

// process an example file, may throw an error if example throws an error
function processExampleFile(directory, file) {
  const filePath = path.join(directory, file);
  const expectedFilePath = filePath + '.out';

  const example = fs.readFileSync(filePath, 'utf8');
  const exampleCode = portalsJSCode + "\n" + example;

  // ignore files in the ignoredFiles array
  if (ignoredFiles.includes(file)) {
    return;
  }

  // create an expected output file if it does not exist
  if (!fs.existsSync(expectedFilePath, 'utf8')) {
    console.warn(`Expected output file not found. Creating ${expectedFilePath}`);
    log.startCapturing();
    eval(exampleCode);
    const output = formatOutput(log.retrieve());
    log.clear();
    log.stopCapturing();
    fs.writeFileSync(expectedFilePath, output, 'utf8');
    return;
  }
}

// read all example files from the example directory, if no output exists, then 
// create the corresponding output file
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

// process the example directory, recusively
processDirectory(exampleDirectory);

// if this message printed then all outputs were generated
console.log('All outputs generated!');