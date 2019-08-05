const express = require('express');
const bodyParser = require('body-parser');

// Create a new instance of express
const app = express();
const port = 9000;

app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());

app.post('/question', function (req, res) {
  const body = req.body;
  console.log(req.body);
  res.set('Content-Type', 'text/plain');
  res.send(`You sent: ${body} to Express`);
})

// Tell our app to listen on port 3000
app.listen(port, function (err) {
  if (err) {
    throw err;
  }

  console.log(`Server started on port ${port}`);
})