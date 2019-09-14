/* eslint-disable promise/catch-or-return */
/* eslint-disable promise/always-return */
/* eslint-disable no-loop-func */
/* eslint-disable promise/no-nesting */
const functions = require('firebase-functions');
const gcm = require('node-gcm');

// Set up the sender with your GCM/FCM API key (declare this once for multiple messages)
const sender = new gcm.Sender('AAAARzEuLYg:APA91bGYPaGcDMMQyweGNXQf4GNHYXERzCqwkxdc_CWhV8CCE24EWVqYfBoVPXKLOgRdxrCRbo4L6lThEgCmVZ8vYMok-VrQ5rCKegdxD-WTu_Ad9NkYBJCSBtAxX-mHKUccg5R6PzDV');

// Prepare a message to be sent
const message = new gcm.Message();

// Specify which registration IDs to deliver the message to
const regTokens = ['cSX9EkhXBgY:APA91bF5-YYnOWQYn2qlcSb3n739-7qVgPS-R_I7m4JJAVffZbhjo5slMrafq3piQ5wM2C1dHWHAu2nlyug3bO1Dgjf_j6dFt21gSWgzOk8aG1eoVELb0kCIL26sXAFAeg0MxHiZHB2f'];

const express = require('express');
const request = require('request');
const cheerio = require('cheerio');
const bodyParser = require('body-parser');
const fs = require('fs');

const admin = require('firebase-admin');
admin.initializeApp(functions.config().firebase);

let db = admin.firestore();

const { getName } = require('./country-list.js');

// Create a new instance of express
const app = express();
const port = process.env.PORT || 5000;

app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());

const SEARCH_RESULT_SELECTOR = 'div.g';
const RIGHT_SNIPPET_SELECTOR = 'div.ifM9O';
const TOP_SNIPPET_SELECTOR = 'div.ifM9O';
const NEWS_SNIPPET_SELECOTR = 'div.rSr7Wd';
const PARENT_ELEMENT_SELECTOR = '#main';
const SEARCH_RESULT_URL = 'div.g div.rc div.r > a';
const URL_SANITIZER = /(https:\/\/translate.google.com\/translate\?hl=en&sl=vi&u=)(.*)(&prev=search)/;
const MAX_REQUEST_SIZE = 6000000;

let oldTime = 0;

function pushNotification(questionNo, result) {
  // Actually send the message
  message.addData('questionNo', questionNo);
  message.addData('result', result);
  sender.send(message, { registrationTokens: regTokens }, (err, response) => {
    if (err) console.error(err);
    else console.log(response);
  });
}

const GoogleOptions = {
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36'
  },
  encoding: 'utf8'
};

const crawlOptions = {
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36'
  },
  encoding: null,
  gzip: true,
  timeout: 2000
};

let score1, score2, score3;
let answerRegex1, answerRegex2, answerRegex3;

app.get('/isShowStarted', (req, res) => {
  // Query if show is started or not
  console.log('REQUEST FOR IS SHOW STARTED: ', Date.now());
  db.collection('clone').get()
  .then((snapshot) => {
    snapshot.forEach((doc) => {
      if (doc.id === '3aEKZ4sLpAnP49tR3UqS' && doc.data().isShowStarted) {
        res.end(doc.data().isShowStarted);
      }
    });
    res.end('false');
  }).catch((err) => {
    console.log('Error getting documents', err);
  });
});

app.get('/result', (req, res) => {
  // Query for result
  console.log('REQUEST FOR RESULT: ', Date.now());
  db.collection('clone').get()
  .then((snapshot) => {
    snapshot.forEach((doc) => {
      if (doc.id === '3aEKZ4sLpAnP49tR3UqS' && doc.data().result.length > 0) {
        res.end("Câu " + doc.data().questionNumber + " : " + doc.data().result);
      }
    });
    res.end('Not yet!');
  }).catch((err) => {
    console.log('Error getting documents', err);
  });
})

app.post('/old', (req, res) => {
  console.log('OLD METHOD: ', Date.now());
  score1 = score2 = score3 = 0;
  let { question, answer1, answer2, answer3, questionNumber } = req.body;

  // Save question to firestore
  let docRef = db.collection('clone').doc('3aEKZ4sLpAnP49tR3UqS');
  let setQuestion = docRef.set({
    isShowStarted: 'true', question, answer1, answer2, answer3, result: '', questionNumber
  });

  console.log("Question: ", question);
  console.log("Answer1: ", answer1);
  console.log("Answer2: ", answer2);
  console.log("Answer3: ", answer3);
  let tokenizedQuestion = tokenizeQuestion(question);

  // Sanitize country names in answers
  const santizedAnswer1 = getName(answer1);
  const santizedAnswer2 = getName(answer2);
  const santizedAnswer3 = getName(answer3);
  if (santizedAnswer1 && santizedAnswer1.length > 0) answer1 = santizedAnswer1;
  if (santizedAnswer2 && santizedAnswer2.length > 0) answer2 = santizedAnswer2;
  if (santizedAnswer3 && santizedAnswer3.length > 0) answer3 = santizedAnswer3;
  console.log(santizedAnswer1 + " " + santizedAnswer2 + " " + santizedAnswer3);
  answerRegex1 = new RegExp(answer1.toLowerCase(), 'g');
  answerRegex2 = new RegExp(answer2.toLowerCase(), 'g');
  answerRegex3 = new RegExp(answer3.toLowerCase(), 'g');
  /*
    Make 4 queries
    1. Only the question
    2. Question with answer 1
    3. Question with answer 2
    4. Question with answer 3
  */
  const query1 = makeQuery(tokenizedQuestion);
  const query2 = makeQuery(tokenizedQuestion) + '+"' + makeQuery(answer1) + '"';
  const query3 = makeQuery(tokenizedQuestion) + '+"' + makeQuery(answer2) + '"';
  const query4 = makeQuery(tokenizedQuestion) + '+"' + makeQuery(answer3) + '"';

  /*
    How to calculate ratings
      Each time an answer appears in top snippet, that answer scores 15 points
      Each time an answer appears in right snippet, that answer scores 10 points
      Each time an answer appears in search results, that answer scores 1 points
      Each time an answer appears in news results, that answer scores 5 points
  */
  Promise.all([
    makeRequest(query1),
    makeRequest(query2),
    makeRequest(query3),
    makeRequest(query4)
  ]).then(() => {
    /*
      One small trick for categorizing questions
        With questions containing "KHÔNG", return the smallest rating
    */
    const max = Math.max(score1, score2, score3);
    const min = Math.min(score1, score2, score3);
    let result, resultInLetter;
    if (question.indexOf('KHÔNG') < 0 && question.indexOf('CHƯA') < 0) {
      score1 === max ? result = answer1 : score2 === max ? result = answer2 : result = answer3;
      score1 === max ? resultInLetter = "Đáp án A" : score2 === max ? resultInLetter = "Đáp án B" : resultInLetter = "Đáp án C";
      // Save result to Firestore
      let docRef = db.collection('clone').doc('3aEKZ4sLpAnP49tR3UqS');
      let setResult = docRef.set({
        question: '',
        answer1: '',
        answer2: '',
        answer3: '',
        isShowStarted: 'true',
        result: resultInLetter,
        questionNumber
      });
      pushNotification(questionNumber, resultInLetter);
      res.end(`${resultInLetter}. ${result} with a score of ${max} in (${score1}, ${score2}, ${score3})`);
      console.log(`${resultInLetter}. ${result} with a score of ${max} in (${score1}, ${score2}, ${score3})`);
    } else {
      score1 === min ? result = answer1 : score2 === min ? result = answer2 : result = answer3;
      score1 === min ? resultInLetter = "Đáp án A" : score2 === min ? resultInLetter = "Đáp án B" : resultInLetter = "Đáp án C";
      // Save result to Firestore
      let docRef = db.collection('clone').doc('3aEKZ4sLpAnP49tR3UqS');
      let setResult = docRef.set({
        question: '',
        answer1: '',
        answer2: '',
        answer3: '',
        isShowStarted: 'true',
        result: resultInLetter,
        questionNumber
      });
      pushNotification(questionNumber, resultInLetter);
      res.end(`${resultInLetter}. ${result} with a score of ${min} in (${score1}, ${score2}, ${score3})`);
      console.log(`${resultInLetter}. ${result} with a score of ${min} in (${score1}, ${score2}, ${score3})`);
    }
    return null;
  }).catch(e => {
    console.error(e);
  });
})

let urls = [];
let kb = '';
let questionNo;
app.post('/ranking', (req, res) => {
  console.log('RANKING METHOD: ', Date.now() - oldTime);
  let { question, answer1, answer2, answer3, questionNumber } = req.body;
  questionNo = questionNumber;
  console.log("Question: ", question);
  console.log("Answer1: ", answer1);
  console.log("Answer2: ", answer2);
  console.log("Answer3: ", answer3);
  // Save question to firestore
  let docRef = db.collection('clone').doc('3aEKZ4sLpAnP49tR3UqS');
  let setQuestion = docRef.set({
    isShowStarted: 'true', question, answer1, answer2, answer3, result: '', questionNumber
  });
  console.log('Save to Firestore: ', Date.now() - oldTime);

  // Sanitize country names in answers
  const santizedAnswer1 = getName(answer1);
  const santizedAnswer2 = getName(answer2);
  const santizedAnswer3 = getName(answer3);
  if (santizedAnswer1 && santizedAnswer1.length > 0) answer1 = santizedAnswer1;
  if (santizedAnswer2 && santizedAnswer2.length > 0) answer2 = santizedAnswer2;
  if (santizedAnswer3 && santizedAnswer3.length > 0) answer3 = santizedAnswer3;
  /*
    Make 4 queries
    1. Only the question
    2. Question with answer 1
    3. Question with answer 2
    4. Question with answer 3
  */
  const query1 = makeQuery(question);
  const query2 = makeQuery(question) + '+"' + makeQuery(answer1) + '"';
  const query3 = makeQuery(question) + '+"' + makeQuery(answer2) + '"';
  const query4 = makeQuery(question) + '+"' + makeQuery(answer3) + '"';

  oldTime = Date.now();
  urls = [];
  Promise.all([makeTestRequest(query1), makeTestRequest(query2), makeTestRequest(query3), makeTestRequest(query4)])
    .then(() => {
      console.log('Time spent on parsing Google SERP: ', Date.now() - oldTime);
      makeCrawlRequest(res, question, answer1, answer2, answer3);
    });
})


function tokenizeQuestion(question) {
  let q = question;
  if (question.indexOf('đâu KHÔNG phải là một ') >= 0) {
    q = q.replace('đâu KHÔNG phải là một ', '');
  }
  if (question.indexOf('đâu KHÔNG phải là ') >= 0) {
    q = q.replace('đâu KHÔNG phải là ', '');
  }
  if (question.indexOf('KHÔNG ') >= 0) {
    q = q.replace('KHÔNG ', '');
  }
  return q;
}

function makeQuery(string) {
  return string.split(' ').join('+');
}

function makeTestRequest(query) {
  GoogleOptions.timeout = 3000;
  GoogleOptions.url = encodeURI(`https://google.com/search?q=${query}`);
  return new Promise((resolve, reject) => {
    request.get(GoogleOptions, (error, response, body) => {
      if (error) {
        console.log(error);
      } else {
        if (body) {
          const $ = cheerio.load(body);
          $(SEARCH_RESULT_URL, PARENT_ELEMENT_SELECTOR).each((index, element) => {
            let url = $(element).attr('href');
            if (url.match(URL_SANITIZER)) {
              url = url.match(URL_SANITIZER)[2];
            }
            if (!urls.includes(url)) {
              urls.push(url);
            }
          });
        }
      }
      resolve(true);
    });
  });
}

function makeCrawlRequest(res, question, answer1, answer2, answer3) {
  let promises = [];
  for (const url of urls) {
    promises.push(Promise.race([new Promise((resolve, reject) => {
      crawlOptions.url = encodeURI(url);
      request(crawlOptions, (error, response, body) => {
        if (error) {
          console.log(error);
        }
        if (body) {
          const $ = cheerio.load(body);
          const s = $.text();
          if (Buffer.byteLength(s) + Buffer.byteLength(kb) >= MAX_REQUEST_SIZE) {
            console.log('Exceed limit: ', Buffer.byteLength(s) + Buffer.byteLength(kb) + ' bytes', Date.now() - oldTime);
            resolve(true);
          }
          kb += s;
        }
        resolve(true);
      });
    }), new Promise(((resolve, reject) => {
      setTimeout(() => {
        resolve(true);
      }, 5000);
    }))]));
  }
  Promise.all(promises).then(() => {
    console.log('Time: ', Date.now() - oldTime);
    console.log('---------------------------------- URL LENGTH: ', urls.length);
    console.log(Buffer.byteLength(kb) + ' bytes');
    params = {
      'question': question,
      'firstChoice': answer1,
      'secondChoice': answer2,
      'thirdChoice': answer3,
      'kb': [kb]
    }
    params = JSON.stringify(params);
    crawlOptions.body = params;
    crawlOptions.encoding = 'utf8';
    crawlOptions.timeout = 5000;
    crawlOptions.url = 'https://us-central1-confetti-faca0.cloudfunctions.net/ranking';
    request.post(crawlOptions, (error, resp, body) => {
      if (body[0] === 'A') result = 'Đáp án A';
      else if (body[0] === 'B') result = 'Đáp án B';
      else if (body[0] === 'C') result = 'Đáp án C';
      else {
        let rand = Date.now();
        if (rand % 3 === 0) result = 'Random Đáp án A';
        if (rand % 3 === 1) result = 'Random Đáp án B';
        if (rand % 3 === 2) result = 'Random Đáp án C';
      }
      // fs.appendFileSync('data.csv', '$[' + question + ']$, $[' + kb + ']$, $[' + body + ']$, $[' + result + ']$, $[' + answer1 + ']$, $[' + answer2 + ']$, $[' + answer3 + ']$');
      urls = [];
      kb = '';
      console.log('Final Time: ', Date.now() - oldTime);
      console.log('Ranking method result: ', body);
      // Save result to Firestore
      let docRef = db.collection('clone').doc('3aEKZ4sLpAnP49tR3UqS');
      let setResult = docRef.set({
        question: '',
        answer1: '',
        answer2: '',
        answer3: '',
        isShowStarted: 'true',
        result,
        questionNumber: questionNo
      });
      res.end(result);
    });
  });
}

function makeRequest(query) {
  return new Promise((resolve, reject) => {
    GoogleOptions.url = encodeURI(`https://google.com/search?q=${query}`);
    request(GoogleOptions, (error, response, body) => {
      if (error) {
        console.log(error);
        reject(error);
      }
      const $ = cheerio.load(body);
      const searchResults = $(SEARCH_RESULT_SELECTOR, PARENT_ELEMENT_SELECTOR).text();
      const rightSnippet = $(RIGHT_SNIPPET_SELECTOR, PARENT_ELEMENT_SELECTOR).text();
      const topSnippet = $(TOP_SNIPPET_SELECTOR, PARENT_ELEMENT_SELECTOR).text();
      const newsSnippet = $(NEWS_SNIPPET_SELECOTR, PARENT_ELEMENT_SELECTOR).text();
      score1 = score1 + calculateRating(searchResults, answerRegex1) + calculateRating(rightSnippet, answerRegex1) * 10 + calculateRating(topSnippet, answerRegex1) * 15 + calculateRating(newsSnippet, answerRegex1) * 5;
      score2 = score2 + calculateRating(searchResults, answerRegex2) + calculateRating(rightSnippet, answerRegex2) * 10 + calculateRating(topSnippet, answerRegex2) * 15 + calculateRating(newsSnippet, answerRegex2) * 5;
      score3 = score3 + calculateRating(searchResults, answerRegex3) + calculateRating(rightSnippet, answerRegex3) * 10 + calculateRating(topSnippet, answerRegex3) * 15 + calculateRating(newsSnippet, answerRegex3) * 5;
      resolve(true);
    });
  });
}

function calculateRating(search, regex) {
  search = search.toLowerCase();
  let matches = search.match(regex);
  return matches ? matches.length : 0;
}

app.listen(port, (err) => {
  if (err) {
    throw err;
  }
  console.log(`Server started on port ${port}`);
});

// Expose Express API as a single Cloud Function:
exports.question = functions.region('asia-east2').https.onRequest(app);