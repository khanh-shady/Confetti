const express = require('express');
const request = require('request');
const bodyParser = require('body-parser');

// Create a new instance of express
const app = express();
const port = 9000;

app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());

app.post('/question', function (req, res) {
  // console.log(req.body.question);
  // const question = req.body.question;
  // const answer1 = req.body.answer1;
  // const answer2 = req.body.answer2;
  // const answer3 = req.body.answer3;
  // let tokenizedQuestion = tokenzieQuestion(question);
  // makeQuery(tokenizedQuestion, answer1, answer2, answer3).then((result) => {
  //   res.send(result);
  // });
  var options = {
    url: `http://www.google.com/search?q=${encodeURI('kẹo+dừa+là+đặc+sản+tỉnh')}`,
    headers: {
      'User-Agent': 'Mozilla/5.0'
    },
    encoding: 'utf8'
  };
  request(options, function (error, response, body) {
    console.log('error:', error); // Print the error if one occurred
    console.log('statusCode:', response && response.statusCode); // Print the response status code if a response was received
    console.log('body:', body); // Print the HTML for the Google homepage.
    res.end(body);
  });
})

// Tell our app to listen on port 3000
app.listen(port, function (err) {
  if (err) {
    throw err;
  }

  console.log(`Server started on port ${port}`);
})

function tokenzieQuestion(question) {

}

function makeQuery(question, answer1, answer2, answer3) {

}

function parseHTML() {

}