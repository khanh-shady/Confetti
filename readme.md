# Installation:
> cd functions

> npm install

# 1. Run on local computer
> npm run start 

and make POST request to localhost:5000/question

# 2. To deploy it to Firebase
> firebase login

> firebase deploy --only functions

All scripts related to server have to be made inside ./functions folder

POST request must consist of 4 params: question, answer1, answer2, answer3
E.G: question: abc, answer1: a, answer2: b, answer3: c