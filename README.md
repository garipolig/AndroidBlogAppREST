# AndroidBlogAppREST
Simple Android application that retrieves blog information from a web service through REST APIs.

The Application will consists in 3 pages:

1) Main Page (entry point when opening the app): the list of Authors.
When clicking on a specific author you will access to a new page: Author Details
2) Author Details: contains a header with the author's details and a listing of all the posts written by this author (title, date, body).
Wheb clicking to a specific post in the list you will access to a new page: Post Details
3) Post Details: contains a header with the post's details and a listing of all the comments about that postm which is ordered by date
of creation, oldest on the top newest on the bottom.

The application will retrieve all the information from a web service (https://sym-json-server.herokuapp.com) using REST APIs.
