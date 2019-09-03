/* eslint-disable promise/catch-or-return */
/* eslint-disable promise/always-return */
/* eslint-disable no-loop-func */
/* eslint-disable promise/no-nesting */
const functions = require('firebase-functions');

const express = require('express');
const request = require('request');
const cheerio = require('cheerio');
const bodyParser = require('body-parser');

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

let oldTime = 0;

const GoogleOptions = {
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36'
  },
  encoding: 'utf8'
};

const CoccocOptions = {
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36',
    'Referer': 'https://coccoc.com/search',
    'Cookie': 'serp_version=26117512/4c4d171;'
  },
  encoding: null,
  gzip: true,
  timeout: 2000
};

let score1, score2, score3;
let answerRegex1, answerRegex2, answerRegex3;

app.post('/google', (req, res) => {
  score1 = score2 = score3 = 0;
  let { question, answer1, answer2, answer3 } = req.body;
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
      res.end(`${resultInLetter}. ${result} with a score of ${max} in (${score1}, ${score2}, ${score3})`);
      console.log(`${resultInLetter}. ${result} with a score of ${max} in (${score1}, ${score2}, ${score3})`);
    } else {
      score1 === min ? result = answer1 : score2 === min ? result = answer2 : result = answer3;
      score1 === min ? resultInLetter = "Đáp án A" : score2 === min ? resultInLetter = "Đáp án B" : resultInLetter = "Đáp án C";
      res.end(`${resultInLetter}. ${result} with a score of ${min} in (${score1}, ${score2}, ${score3})`);
      console.log(`${resultInLetter}. ${result} with a score of ${min} in (${score1}, ${score2}, ${score3})`);
    }
    return null;
  }).catch(e => {
    console.error(e);
  });
})

let urls = [];
let kb;
app.post('/coccoc', (req, res) => {
  let { question, answer1, answer2, answer3 } = req.body;
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
  const query1 = question;
  const query2 = question + ' "' + answer1 + '"';
  const query3 = question + ' "' + answer2 + '"';
  const query4 = question + ' "' + answer3 + '"';

  oldTime = Date.now();
  urls = [];
  Promise.all([makeTestRequest(query1), makeTestRequest(query2), makeTestRequest(query3), makeTestRequest(query4)])
    .then(() => {
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
  CoccocOptions.timeout = 2000;
  CoccocOptions.url = `https://coccoc.com/composer?_=${Date.now()}&p=0&q=${encodeURIComponent(query)}&reqid=asd`;
  return new Promise((resolve, reject) => {
    request.get(CoccocOptions, (error, response, body) => {
      if (error) {
        console.log(error);
      } else {
        console.log(query);
        try {
          let search_results = JSON.parse(body).search.search_results;
          for (const result of search_results) {
            if (!urls.includes(result.url)) {
              urls.push(result.url);
            }
          }
        } catch(e) {
          console.log(e);
        }
      }
      resolve(true);
    });
  });
}

function makeCrawlRequest(res, question, answer1, answer2, answer3) {
  let promises = [];
  CoccocOptions.timeout = 1000;
  for (const url of urls) {
    promises.push(new Promise((resolve, reject) => {
      CoccocOptions.url = encodeURI(url);
      request(CoccocOptions, (error, response, body) => {
        if (error) {
          console.log(error);
        }
        if (body) {
          const $ = cheerio.load(body);
          kb += $.text();
        }
        console.log('Time promise: ', Date.now() - oldTime);
        resolve(true);
      });
    }));
  }
  Promise.all(promises).then(() => {
    console.log('Time: ', Date.now() - oldTime);
    console.log('---------------------------------- URL LENGTH: ', urls.length);
    params = {
      'question': question,
      'firstChoice': answer1,
      'secondChoice': answer2,
      'thirdChoice': answer3,
      'kb': [kb]
    }
    params = JSON.stringify(params);
    CoccocOptions.body = params;
    CoccocOptions.encoding = 'utf8';
    CoccocOptions.url = 'https://asia-east2-confetti-faca0.cloudfunctions.net/ranking';
    request.post(CoccocOptions, (error, resp, body) => {
      urls = [];
      kb = '';
      console.log('Final Time: ', Date.now() - oldTime);
      if (body === 'A') res.end('Đáp án A');
      else if (body === 'B') res.end('Đáp án B');
      else if (body === 'C') res.end('Đáp án C');
      else {
        let rand = Math.floor(Math.random(100));
        if (rand % 3 === 0) res.end('Random Đáp án A');
        if (rand % 3 === 1) res.end('Random Đáp án B');
        if (rand % 3 === 2) res.end('Random Đáp án C');
      }
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

// Tell our app to listen on port 3000
app.listen(port, (err) => {
  if (err) {
    throw err;
  }
  console.log(`Server started on port ${port}`);
});

// Expose Express API as a single Cloud Function:
exports.question = functions.region('asia-east2').https.onRequest(app);