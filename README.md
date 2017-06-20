# Custom Spring Data Rest Extension [![Build Status](https://travis-ci.org/christos-karalis/spring-data-rest-extension.svg?branch=master)](https://travis-ci.org/christos-karalis/spring-data-rest-extension) [![codecov](https://codecov.io/gh/christos-karalis/spring-data-rest-extension/branch/master/graph/badge.svg)](https://codecov.io/gh/christos-karalis/spring-data-rest-extension)

It is an extension to the functionality of Spring Data Rest (currently only working with Spring Data JPA). 
It provides rest services to implement advanced searches and saves by the provided entity classes and generated 
Querydsl query types. Additionally, it requires the @RepositoryRestResource that exports the rest services.

Examples of search json:
    
```json
{
  "operator" : "OR",
  "operands" : {
      "_comment" : "matches the records name ingore case and it contains 'John'",
      "and" : {
        "name" : "David",
        "surname" : "Mend"
      },
      "or" : [{
            "city" : "http://localhost/city/2"
          },
          {
            "city" : {
              "county" : "http://localhost/county/2"
            }
          }
      
      ],
      "birthDate" : {
        "from" : 631152000000,
        "to" : 788918400000
      },
      "or" : [
          {
              "visits" : {
                "from" : 100
              }, 
              "visits" : 1
          }
      ]
  }
}
```
    
More details on [Wiki](../../wiki)
