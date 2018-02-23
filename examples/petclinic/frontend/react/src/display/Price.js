import React from 'react'

const Price = ({price, currency}) => {

  let localPrice = price.toFixed(2);

  switch(currency) {
    case 'USD':
      localPrice = `$${localPrice}`;
      break;
    case 'EUR':
      localPrice = `€${localPrice}`;
      break;
    case 'GBP':
      localPrice = `£${localPrice}`;
      break;
    default:
      localPrice = `${localPrice}`
  }


  return <span className="badge badge-secondary">{localPrice}</span>
}

export default Price