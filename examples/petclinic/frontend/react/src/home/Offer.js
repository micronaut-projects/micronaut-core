import React from 'react'
import config from "../config";
import Price from "../display/Price";

const Offer = ({offer}) => offer ? <div id="offers">


  <div className="jumbotron jumbotron-fluid" style={{
    backgroundImage: `url(${config.SERVER_URL}/images/${offer.pet.image})`,
    backgroundPosition: 'center',
    color: 'white'
  }}>
    <div className="container">
      <h1 className="display-4">{offer.pet.name}</h1>
      <p className="lead">{offer.description}</p>
      <h3><Price price={offer.price} currency={offer.currency} /></h3>
      <p>
        <small>{offer.pet.vendor} | {offer.pet.type}</small>
      </p>
    </div>
  </div>
</div> : <div className="jumbotron jumbotron-fluid" style={{
  backgroundImage: `url(${config.SERVER_URL}/images/missing.png)`
}}>
  <div className="container">
    <h1 className="display-4">Loading...</h1>
  </div>
</div>

export default Offer;